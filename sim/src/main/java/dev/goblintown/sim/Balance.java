package dev.goblintown.sim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase-1 economy table (H5, data-driven content): the balance tables loaded
 * from plain JSON under {@code data/} at the repo root. Balance edits must
 * never require a code change; nothing in this file may hardcode game
 * constants — those live in the JSON.
 *
 * <p>Units and conversions (DR-0003): rates are expressed in the data files
 * as whole units per <em>second</em> (tick-rate independent) and converted to
 * per-tick Q32.32 fixed-point exactly once, here, at load. {@code capacity}
 * and XP thresholds are whole units.
 */
public final class Balance {

    /**
     * TICKS_PER_SECOND is a save-version constant (DR-0001: the tick economy
     * run rate is recorded in saves). Changing it requires a save migration,
     * never a silent edit.
     */
    public static final int TICKS_PER_SECOND = 60;

    /** A stockpile resource: id + storage cap (whole units → fixed at load). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceDef(String id, long capacity) {
        public long capFixed() {
            return Fixed.fromUnits(capacity);
        }
    }

    /**
     * A worker role. Phase-1 model (docs/03-idle-loop.md §Formulas, all
     * numbers PLACEHOLDER): a role produces one resource at a flat per-tick
     * rate and accrues XP; level-ups are events, output does not scale yet
     * (keeps Phase-1 production linear so closed-form catch-up is exact).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoleDef(
            String id,
            String produces,
            long unitsPerSecond,
            long xpPerSecond,
            long levelUpBaseXp,
            long levelUpXpGrowth) {

        /** Per-tick production in Q32.32 fixed: rate·ONE/60, truncated once,
         *  identically on every load (DR-0003). */
        public long amountPerTickFixed() {
            return Fixed.fromUnits(unitsPerSecond) / TICKS_PER_SECOND;
        }

        /** Per-tick XP in Q32.32 fixed (same conversion rule). */
        public long xpPerTickFixed() {
            return Fixed.fromUnits(xpPerSecond) / TICKS_PER_SECOND;
        }

        /** XP pool at which level {@code n} completes (linear growth).
         *  Monotonically increasing in n — required for the closed-form
         *  level-up boundaries in {@link SimEngine#catchUp}. */
        public long levelThresholdFixed(int level) {
            return Fixed.fromUnits(levelUpBaseXp)
                    + Fixed.mul(Fixed.fromUnits(levelUpXpGrowth), Fixed.fromUnits(level - 1));
        }
    }

    private final List<ResourceDef> resources;
    private final List<RoleDef> roles;
    private final Map<String, ResourceDef> resourcesById;
    private final Map<String, RoleDef> rolesById;
    private final Map<String, Long> foundingSupplies;

    private Balance(List<ResourceDef> resources, List<RoleDef> roles,
                    Map<String, Long> foundingSupplies) {
        this.resources = List.copyOf(resources);
        this.roles = List.copyOf(roles);
        this.resourcesById = index(this.resources, ResourceDef::id, "resource");
        this.rolesById = index(this.roles, RoleDef::id, "role");
        this.foundingSupplies = validateSupplies(foundingSupplies, this.resourcesById);
        for (RoleDef role : this.roles) {
            if (role.produces() != null && !resourcesById.containsKey(role.produces())) {
                throw new IllegalArgumentException("role " + role.id()
                        + " produces unknown resource: " + role.produces());
            }
        }
    }

    /**
     * The stockpile a fresh founding starts with, in whole units (docs/09:
     * the Phase-2a stand-in for founding map-node loot; the real founding
     * economy comes with world-gen). Clamped at the resource's cap — a
     * founding supply above capacity would create an instant StockpileFull.
     */
    public long foundingSupply(String resourceId) {
        long units = foundingSupplies.getOrDefault(resourceId, 0L);
        return Math.min(units, resource(resourceId).capacity());
    }

    public List<ResourceDef> resources() {
        return resources;
    }

    public List<RoleDef> roles() {
        return roles;
    }

    public ResourceDef resource(String id) {
        ResourceDef def = resourcesById.get(id);
        if (def == null) {
            throw new IllegalArgumentException("unknown resource: " + id);
        }
        return def;
    }

    public RoleDef role(String id) {
        RoleDef def = rolesById.get(id);
        if (def == null) {
            throw new IllegalArgumentException("unknown role: " + id);
        }
        return def;
    }

    /** For cross-table validation (e.g. the buildings loader). */
    public boolean hasResource(String id) {
        return resourcesById.containsKey(id);
    }

    /** For cross-table validation (e.g. the buildings loader). */
    public boolean hasRole(String id) {
        return rolesById.containsKey(id);
    }

    /** Parses + validates a balance table from JSON. Throws
     *  {@link IllegalArgumentException} on structural or semantic errors —
     *  invalid data is a load-time validation error (rules/02 §Java). */
    public static Balance fromJson(InputStream in) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Raw raw = mapper.readValue(in, Raw.class);
            return of(raw);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read balance table", e);
        }
    }

    /** Visible for tests: validates and builds from already-parsed parts. */
    public static Balance of(Raw raw) {
        if (raw.resources == null || raw.resources.isEmpty()) {
            throw new IllegalArgumentException("balance table needs at least one resource");
        }
        if (raw.roles == null || raw.roles.isEmpty()) {
            throw new IllegalArgumentException("balance table needs at least one role");
        }
        for (ResourceDef r : raw.resources) {
            requireId(r.id(), "resource");
            if (r.capacity() < 0 || r.capacity() > Fixed.unitsFloor(Long.MAX_VALUE)) {
                throw new IllegalArgumentException("resource " + r.id() + ": capacity out of range");
            }
        }
        for (RoleDef role : raw.roles) {
            requireId(role.id(), "role");
            if (role.unitsPerSecond() < 0 || role.xpPerSecond() < 0
                    || role.levelUpBaseXp() <= 0 || role.levelUpXpGrowth() < 0) {
                throw new IllegalArgumentException("role " + role.id() + ": rates/thresholds out of range");
            }
        }
        return new Balance(raw.resources, raw.roles, raw.foundingSupplies == null
                ? Map.of() : raw.foundingSupplies);
    }

    private static Map<String, Long> validateSupplies(Map<String, Long> supplies,
                                                      Map<String, ResourceDef> resourcesById) {
        for (Map.Entry<String, Long> entry : supplies.entrySet()) {
            if (!resourcesById.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("founding supply for unknown resource: " + entry.getKey());
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("negative founding supply: " + entry.getKey());
            }
        }
        return Map.copyOf(supplies);
    }

    private static void requireId(String id, String kind) {
        if (id == null || !id.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException(kind + " id missing or not machine-style: " + id);
        }
    }

    private static <T> Map<String, T> index(List<T> defs, java.util.function.Function<T, String> id,
                                            String kind) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T def : defs) {
            String key = id.apply(def);
            T prev = map.put(key, def);
            if (prev != null) {
                throw new IllegalArgumentException("duplicate " + kind + " id: " + key);
            }
        }
        return Map.copyOf(map);
    }

    /** Raw Jackson shape of economy.json. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Raw {
        public List<ResourceDef> resources;
        public List<RoleDef> roles;
        public Map<String, Long> foundingSupplies;
    }
}
