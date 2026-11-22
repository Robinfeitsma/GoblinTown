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
 * The building table (H5, data-driven content): parsed from
 * {@code data/buildings.json} against the loaded economy table — building
 * costs/staffing reference resources and roles, so cross-table validation
 * fails at load, never mid-game (rules/02 §2).
 *
 * <p>Units and semantics (docs/09, approved): {@code cost} in whole units,
 * deducted at build confirmation; {@code progressCost} in progress-points,
 * builders add 1/tick; {@code capacityBonus} in whole units converted to
 * Q32.32 once at load (DR-0003).
 */
public final class Buildings {

    /** One resource requirement line of a building cost. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostEntry(String resource, long amount) {
        public CostEntry {
            if (amount < 0) {
                throw new IllegalArgumentException("negative cost amount for " + resource);
            }
        }
    }

    /**
     * A building type definition. Jackson shape: optional fields arrive as
     * null / default to 0 / empty — accessor methods normalize them so the
     * engine never sees optionality.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BuildingDef(
            String id,
            String hosts,
            Integer slots,
            List<CostEntry> cost,
            Long progressCost,
            Boolean startUnlocked,
            String requires,
            int grantsTier,
            Map<String, Long> capacityBonus) {

        public BuildingDef {
            capacityBonus = capacityBonus == null ? Map.of() : Map.copyOf(capacityBonus);
            cost = cost == null ? List.of() : List.copyOf(cost);
        }

        public int slotCount() {
            return slots == null ? 0 : slots;
        }

        /** Per-tick progress per builder is 1 whole point (docs/09). */
        public long progressCostLong() {
            if (progressCost == null || progressCost <= 0) {
                throw new IllegalArgumentException("building " + id + ": progressCost must be positive");
            }
            return progressCost;
        }

        public boolean isStartUnlocked() {
            return startUnlocked == null || startUnlocked;
        }

        /** Fixed-point capacity bonus for a resource (0 if none). */
        public long capacityBonusFixed(String resourceId) {
            Long units = capacityBonus.get(resourceId);
            return units == null ? 0L : Fixed.fromUnits(units);
        }
    }

    private final List<BuildingDef> defs;
    private final Map<String, BuildingDef> defsById;

    private Buildings(List<BuildingDef> defs, Balance balance) {
        this.defs = List.copyOf(defs);
        this.defsById = index(this.defs);
        for (BuildingDef def : this.defs) {
            validate(def, balance);
        }
    }

    public List<BuildingDef> defs() {
        return defs;
    }

    public BuildingDef type(String id) {
        BuildingDef def = defsById.get(id);
        if (def == null) {
            throw new IllegalArgumentException("unknown building type: " + id);
        }
        return def;
    }

    public boolean hasType(String id) {
        return defsById.containsKey(id);
    }

    /** True for the {@link #none()} table — role-only engines. */
    public boolean isEmpty() {
        return defs.isEmpty();
    }

    /** Empty table — role-only engines (synthetic tests, pre-data builds). */
    public static Buildings none() {
        return new Buildings(List.of(), null);
    }

    /** Parses + validates the building table against an economy table. */
    public static Buildings fromJson(InputStream in, Balance balance) {        try {
            ObjectMapper mapper = new ObjectMapper();
            Raw raw = mapper.readValue(in, Raw.class);
            return of(raw, balance);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read building table", e);
        }
    }

    /** Visible for tests: validates and builds from already-parsed parts. */
    public static Buildings of(Raw raw, Balance balance) {
        if (raw == null || raw.buildings == null || raw.buildings.isEmpty()) {
            throw new IllegalArgumentException("building table needs at least one definition");
        }
        return new Buildings(raw.buildings, balance);
    }

    private void validate(BuildingDef def, Balance balance) {
        if (def.id == null || !def.id.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("building id missing or not machine-style: " + def.id);
        }
        def.progressCostLong();
        if (def.hosts != null && !balance.hasRole(def.hosts)) {
            throw new IllegalArgumentException("building " + def.id + " hosts unknown role: " + def.hosts);
        }
        if (def.hosts == null && def.slotCount() > 0) {
            throw new IllegalArgumentException("building " + def.id + ": slots without hosts");
        }
        if (def.slotCount() < 0) {
            throw new IllegalArgumentException("building " + def.id + ": negative slots");
        }
        for (CostEntry entry : def.cost) {
            if (entry.resource() == null || !balance.hasResource(entry.resource())) {
                throw new IllegalArgumentException("building " + def.id
                        + ": cost references unknown resource " + entry.resource());
            }
            if (entry.amount() <= 0) {
                throw new IllegalArgumentException("building " + def.id
                        + ": cost amounts must be positive (" + entry.resource() + ")");
            }
        }
        if (!def.isStartUnlocked() && def.requires == null) {
            throw new IllegalArgumentException("building " + def.id
                    + ": locked buildings must declare a 'requires' type");
        }
        if (def.requires != null && !hasType(def.requires)) {
            throw new IllegalArgumentException("building " + def.id
                    + " requires unknown building type: " + def.requires);
        }
        for (Map.Entry<String, Long> entry : def.capacityBonus().entrySet()) {
            if (!balance.hasResource(entry.getKey())) {
                throw new IllegalArgumentException("building " + def.id
                        + ": capacityBonus for unknown resource " + entry.getKey());
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("building " + def.id + ": negative capacityBonus");
            }
        }
    }

    private Map<String, BuildingDef> index(List<BuildingDef> defs) {
        Map<String, BuildingDef> map = new LinkedHashMap<>();
        for (BuildingDef def : defs) {
            BuildingDef prev = map.put(def.id(), def);
            if (prev != null) {
                throw new IllegalArgumentException("duplicate building id: " + def.id());
            }
        }
        return Map.copyOf(map);
    }

    /** Raw Jackson shape of buildings.json. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Raw {
        public List<BuildingDef> buildings;
    }
}
