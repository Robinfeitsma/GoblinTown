package dev.goblintown.sim;

import dev.goblintown.sim.command.Command;
import dev.goblintown.sim.event.SimEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tick-town engine (docs/03-idle-loop.md §Production model, docs/09
 * Buildings &amp; Construction): one command path in, deterministic ticks and a
 * closed-form offline catch-up out.
 *
 * <p>Contract (H1/H2, rules/02 §2–3):
 * <ul>
 *   <li>{@link #tick} advances {code n} ticks one at a time.</li>
 *   <li>{@link #catchUp} advances {code n} ticks in closed form — O(roles +
 *       events), never O(ticks) — and produces a state BYTE-IDENTICAL to
 *       sequential {@link #tick} (rules/03 category 2: exact tolerance).
 *       Valid while production and construction progress stay linear in
 *       ticks; capacity bonuses (docs/09 re-segment the closed form at
 *       completion boundaries, solved exactly (DR-0003).</li>
 *   <li>Same initial state + same ordered commands → same result, bit for
 *       bit (H1).</li>
 * </ul>
 *
 * <p>The engine owns its working state as plain data (rules/02 §1: no hidden
 * rules in the data classes); {@link #snapshot()} hands out immutable
 * {@link GameState} records — the only state shape that ever crosses a
 * boundary (M3: save, renderers, headless output).
 *
 * <p>Tick order inside one tick (binding for determinism): the tick counter
 * increments first; then roles run in {@link Balance#roles()} order — within
 * a role, XP accrues (level-ups fire) before production/clamping; LAST the
 * construction phase advances sites. A capacity bonus therefore applies
 * from the tick AFTER the one the building completed on — the same ordering
 * the catch-up segments implement. Builders produce nothing: builder time
 * is a labor mode of its own (docs/09 §Worker accounting).
 */
public final class SimEngine {

    /**
     * Arithmetic-safety guard, NOT a gameplay constant: Q32.32 (DR-0003)
     * represents at most ~2.15e9 whole units, so an unbounded linear XP
     * threshold curve would eventually overflow and wrap (freezing the sim
     * in an infinite level-up loop). MAX_LEVEL sits far beyond any meaningful
     * progression; XP still accrues past it, no more level-ups fire. If
     * balance ever wants real levels here, that is a data + DR change.
     */
    static final int MAX_LEVEL = 1_000_000;

    private final Balance balance;
    private final Buildings buildingsTable;

    /**
     * The canonical event order of {@code tickOnce}: by tick; at equal ticks
     * it follows the phase order (roles before construction), roles by their
     * position in {@link Balance#roles()}, level-ups before production within
     * a role; building events tie-break by building id (= creation order:
     * ids carry a zero-padded serial). This must remain EXACTLY the order the
     * tickOnce phases emit, or catch-up timelines diverge from live play (H1).
     */
    private final Comparator<SimEvent> eventOrder;

    // Working state — plain data, mutated in place, never handed out except
    // as GameState snapshots.
    private long tick;
    private final Map<String, Long> stockpile;
    private final Map<String, Integer> assignments;
    private final Map<String, Long> roleXp;
    private final Map<String, Integer> roleLevel;
    private long rngState;
    private final List<SimEvent> events;
    private final List<Building> buildSites;
    private final Map<String, Integer> builders;
    private long nextBuildingSerial;

    /**
     * Effective caps cache (base caps + completed capacity bonuses), lazily
     * rebuilt after every completion. FIXED-point values: base caps are
     * fixed at load; bonuses convert once (DR-0003).
     */
    private Map<String, Long> capCache;

    /** Factory: fresh game from tables + seed (seed → rngState). */
    public static SimEngine newGame(Balance balance, Buildings buildings, long seed) {
        return new SimEngine(balance, buildings, GameState.initial(balance, seed));
    }

    /** Convenience for role-only tables (synthetic tests): no buildings. */
    public SimEngine(Balance balance, GameState state) {
        this(balance, Buildings.none(), state);
    }

    public SimEngine(Balance balance, Buildings buildings, GameState state) {
        this.balance = balance;
        this.buildingsTable = buildings;
        // eventRoleIndex: role position for RoleLevelUp; resourceId →
        // producing role's position for StockpileFull (both = position in
        // the tickOnce role loop).
        List<Balance.RoleDef> defs = balance.roles();
        for (int i = 0; i < defs.size(); i++) {
            this.eventRoleIndex.put(defs.get(i).id(), i);
            if (defs.get(i).produces() != null) {
                this.eventRoleIndex.putIfAbsent(defs.get(i).produces(), i);
            }
        }
        this.eventOrder = Comparator
                .comparingLong(SimEvent::tick)
                .thenComparingInt(this::sourceRank)
                .thenComparingInt(SimEngine::kindRank)
                .thenComparing(e -> e instanceof SimEvent.BuildingFinished finished
                        ? finished.buildingId()
                        : "");
        this.tick = state.tick();
        this.stockpile = new LinkedHashMap<>(state.stockpile());
        this.assignments = new LinkedHashMap<>(state.assignments());
        this.roleXp = new LinkedHashMap<>(state.roleXp());
        this.roleLevel = new LinkedHashMap<>(state.roleLevel());
        this.rngState = state.rngState();
        this.events = new ArrayList<>(state.events());
        this.buildSites = new ArrayList<>(state.buildings());
        this.builders = new LinkedHashMap<>(state.builderAssignments());
        this.nextBuildingSerial = state.nextBuildingSerial();
        this.capCache = null;
    }

    /** Current state as an immutable snapshot (saved, hashed, rendered). */
    public GameState snapshot() {
        return new GameState(tick, new LinkedHashMap<>(stockpile), new LinkedHashMap<>(assignments),
                new LinkedHashMap<>(roleXp), new LinkedHashMap<>(roleLevel), rngState,
                new ArrayList<>(events), new ArrayList<>(buildSites),
                new LinkedHashMap<>(builders), nextBuildingSerial);
    }

    /** Applies a command at the current tick (command list drives H1).
     *  instanceof dispatch, not pattern switch: we compile to release 17
     *  (DR-0001), where switch patterns don't exist yet. */
    public void execute(Command command) {
        if (command instanceof Command.AssignWorkers assign) {
            assignWorkers(balance.role(assign.roleId()), assign.count());
        } else if (command instanceof Command.UnassignWorkers unassign) {
            unassignWorker(balance.role(unassign.roleId()), unassign.count());
        } else if (command instanceof Command.Build build) {
            build(build.buildingType());
        } else if (command instanceof Command.CancelSite cancel) {
            cancelSite(cancel.buildingId());
        } else if (command instanceof Command.AssignBuilders assignBuilders) {
            assignBuilders(assignBuilders.buildingId(), assignBuilders.count());
        } else if (command instanceof Command.UnassignBuilders unassignBuilders) {
            unassignBuilders(unassignBuilders.buildingId(), unassignBuilders.count());
        } else {
            throw new IllegalArgumentException("unknown command: " + command);
        }
    }

    /** Current tick (the only time in sim; wall-clock never enters, H1). */
    public long tick() {
        return tick;
    }

    /**
     * The town tier: the highest {@code grantsTier} among COMPLETE buildings
     * (docs/09 §Town tiers — derived, never stored: state stands alone).
     */
    public int townTier() {
        int tier = 0;
        for (Building site : buildSites) {
            if (site.complete()) {
                tier = Math.max(tier, buildingsTable.type(site.type()).grantsTier());
            }
        }
        return tier;
    }

    /**
     * Assignment capacity of a role: the {@code slots} summed over all
     * COMPLETE buildings hosting it (docs/09: an assignment needs a
     * building to work at; slots between 0 and the role's capacity).
     */
    public int roleCapacity(String roleId) {
        // role-only tables (synthetic tests, pre-data builds) keep the
        // Phase-1 free-assignment semantics
        if (buildingsTable.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int slots = 0;
        for (Building site : buildSites) {
            if (site.complete()
                    && roleId.equals(buildingsTable.type(site.type()).hosts())) {
                slots += buildingsTable.type(site.type()).slotCount();
            }
        }
        return slots;
    }

    /** Advances exactly {@code n} ticks, one at a time. */
    public void tick(int n) {
        for (int i = 0; i < n; i++) {
            tickOnce();
        }
    }

    /**
     * Advances {@code n} ticks via closed-form math (H2: bounded time — an
     * 8h absence (~1.73M ticks at 60 Hz) must never be a per-tick loop).
     * Produces the same state as {@code tick(n)} would, bit for bit.
     */
    public void catchUp(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("negative elapsed ticks");
        }
        if (n == 0) {
            return;
        }
        long startTick = tick;

        // XP: linear growth → solve level boundaries exactly, once for the
        // whole window — XP accrues regardless of caps or site state (docs/08
        // OQ-2: XP continues while stockpiles are full; docs/09: builders
        // accrue no XP, and builders were not producing anyway).
        for (Balance.RoleDef role : balance.roles()) {
            int workers = assignments.get(role.id());
            if (workers > 0) {
                solveLevelUps(role, workers, startTick, n);
            }
        }

        // Production + construction: production runs at the caps of its
        // segment; capacity bonuses only apply from the tick AFTER the
        // completion (tick order roles → construction), so the closed form
        // re-segments at the earliest completion offset and is exact.
        long start = tick;
        long remaining = n;
        while (remaining > 0) {
            long completionOffset = earliestCompletionOffset(); // ≥ 1
            long length = Math.min(remaining, completionOffset);
            produceSegment(start, length);
            // construction phase for each of the `length` ticks (index loop:
            // entries are REPLACED in place here, never removed)
            for (int i = 0; i < buildSites.size(); i++) {
                Building site = buildSites.get(i);
                if (site.complete()) {
                    continue;
                }
                Integer assigned = builders.get(site.id());
                if (assigned == null || assigned <= 0) {
                    continue;
                }
                long progressed = site.progress() + (long) assigned * length;
                if (progressed >= buildingsTable.type(site.type()).progressCostLong()) {
                    progressed = buildingsTable.type(site.type()).progressCostLong();
                }
                buildSites.set(i, site.withProgress(progressed));
            }
            completeReachingSites(start + length);
            start += length;
            remaining -= length;
        }
        // tickOnce emits events strictly chronologically (ties broken by
        // phase/role order — see eventOrder). The closed-form path solved
        // roles one at a time, so restore the tickOnce order here; must be
        // identical (H1).
        events.sort(eventOrder);
        tick = startTick + n;
    }

    // ------------------------------------------------------------------
    // tick-by-tick path
    // ------------------------------------------------------------------

    private void tickOnce() {
        tick++;
        for (Balance.RoleDef role : balance.roles()) {
            int workers = assignments.get(role.id());
            if (workers <= 0) {
                continue;
            }
            accrueXp(role, workers);
            if (role.produces() != null) {
                produce(role, workers);
            }
        }
        construct();
    }

    private void accrueXp(Balance.RoleDef role, int workers) {
        long gain = (long) workers * role.xpPerTickFixed();
        if (gain == 0) {
            return;
        }
        long xp = roleXp.get(role.id()) + gain;
        roleXp.put(role.id(), xp);
        int level = roleLevel.get(role.id());
        while (level < MAX_LEVEL && xp >= role.levelThresholdFixed(level)) {
            level++;
            events.add(new SimEvent.RoleLevelUp(tick, role.id(), level));
        }
        roleLevel.put(role.id(), level);
    }

    private void produce(Balance.RoleDef role, int workers) {
        Balance.ResourceDef resource = balance.resource(role.produces());
        long cap = effectiveCap(resource.id());
        long amount = stockpile.get(resource.id());
        if (amount >= cap) {
            return;
        }
        long next = amount + (long) workers * role.amountPerTickFixed();
        if (next >= cap) {
            events.add(new SimEvent.StockpileFull(tick, resource.id()));
            next = cap;
        }
        stockpile.put(resource.id(), next);
    }

    /**
     * The construction phase (docs/09): buildings in state order — sites
     * complete at the tick their progress first reaches progressCost; the
     * site's builders stand down (they produced nothing — separate pool),
     * slots/capacity apply from the next tick.
     */
    private void construct() {
        for (int i = 0; i < buildSites.size(); i++) {
            Building site = buildSites.get(i);
            if (site.complete()) {
                continue;
            }
            Integer assigned = builders.get(site.id());
            if (assigned == null || assigned <= 0) {
                continue;
            }
            long progressed = site.progress() + assigned;
            if (progressed >= buildingsTable.type(site.type()).progressCostLong()) {
                buildSites.set(i, site.withProgress(
                                buildingsTable.type(site.type()).progressCostLong())
                        .completed());
                builders.remove(site.id());
                events.add(new SimEvent.BuildingFinished(
                        tick, site.id(), site.type()));
                capCache = null; // capacity bonuses may now apply
            } else {
                buildSites.set(i, site.withProgress(progressed));
            }
        }
    }

    // ------------------------------------------------------------------
    // closed-form catch-up path (must mirror tickOnce exactly — rules/03
    // category 2 gates this with exact-equality tests)
    // ------------------------------------------------------------------

    /**
     * Closed-form level-boundary solver — the XP half of the old
     * {@code catchUpRole}. A boundary met with `consumed` ticks "elapsed"
     * fires at {@code startTick + consumed}, matching tickOnce's counter
     * (the tick increments before XP accrues).
     */
    private void solveLevelUps(Balance.RoleDef role, int workers, long startTick, int ticks) {
        long gainPerTick = (long) workers * role.xpPerTickFixed();
        long xp = roleXp.get(role.id());
        int level = roleLevel.get(role.id());
        if (gainPerTick > 0) {
            long consumed = 0;
            long remaining = ticks;
            long threshold = role.levelThresholdFixed(level);
            while (true) {
                if (level >= MAX_LEVEL || xp >= threshold) {
                    if (level >= MAX_LEVEL) {
                        break; // guard, see MAX_LEVEL — mirrors accrueXp
                    }
                    level++;
                    roleLevel.put(role.id(), level);
                    events.add(new SimEvent.RoleLevelUp(startTick + consumed, role.id(), level));
                    threshold = role.levelThresholdFixed(level);
                    continue;
                }
                if (remaining == 0) {
                    break;
                }
                long needed = Fixed.ceilDiv(threshold - xp, gainPerTick);
                if (needed > remaining) {
                    xp += gainPerTick * remaining;
                    remaining = 0;
                } else {
                    xp += gainPerTick * needed;
                    consumed += needed;
                    remaining -= needed;
                }
            }
            roleXp.put(role.id(), xp);
        }
    }

    /**
     * Closed-form production of one segment with the caps AS OF the segment
     * start (no completion occurs inside a segment — boundaries end them).
     * A fill transition fires exactly where tickOnce's produce clamps: the
     * first tick with {@code amount + gain >= cap}.
     */
    private void produceSegment(long segmentStart, long length) {
        for (Balance.RoleDef role : balance.roles()) {
            if (role.produces() == null) {
                continue;
            }
            int workers = assignments.get(role.id());
            if (workers <= 0) {
                continue;
            }
            Balance.ResourceDef resource = balance.resource(role.produces());
            long cap = effectiveCap(resource.id());
            long amount = stockpile.get(resource.id());
            long gainPerTick = (long) workers * role.amountPerTickFixed();
            if (amount < cap && gainPerTick > 0) {
                long fillOffset = Fixed.ceilDiv(cap - amount, gainPerTick);
                if (fillOffset <= length) {
                    events.add(new SimEvent.StockpileFull(segmentStart + fillOffset, resource.id()));
                    stockpile.put(resource.id(), cap);
                } else {
                    stockpile.put(resource.id(), amount + gainPerTick * length);
                }
            }
        }
    }

    /** Ticks (≥ 1) until the FIRST site with builders completes; never
     *  called with none pending at a positive remaining count. */
    private long earliestCompletionOffset() {
        long earliest = Long.MAX_VALUE;
        for (Building site : buildSites) {
            if (site.complete()) {
                continue;
            }
            Integer assigned = builders.get(site.id());
            if (assigned == null || assigned <= 0) {
                continue;
            }
            long cost = buildingsTable.type(site.type()).progressCostLong();
            long offset = Fixed.ceilDiv(cost - site.progress(), assigned);
            earliest = Math.min(earliest, offset);
        }
        return earliest;
    }

    /** Completes (and emits for) every site whose progress reached its cost
     *  during the tick batch ending at absolute tick {@code atTick} — list
     *  order = creation order = building-id order (H1-safe tie-break). */
    private void completeReachingSites(long atTick) {
        for (int i = 0; i < buildSites.size(); i++) {
            Building site = buildSites.get(i);
            if (site.complete()) {
                continue;
            }
            Integer assigned = builders.get(site.id());
            if (assigned == null || assigned <= 0) {
                continue;
            }
            Buildings.BuildingDef def = buildingsTable.type(site.type());
            if (site.progress() >= def.progressCostLong()) {
                buildSites.set(i, site.completed());
                builders.remove(site.id());
                events.add(new SimEvent.BuildingFinished(atTick, site.id(), site.type()));
                capCache = null;
            }
        }
    }

    // ------------------------------------------------------------------
    // commands (validation lives here, at the state boundary — rules/02)
    // ------------------------------------------------------------------

    private void assignWorkers(Balance.RoleDef role, int count) {
        int capacity = roleCapacity(role.id());
        int current = assignments.get(role.id());
        if (current + count > capacity) {
            throw new IllegalArgumentException("role " + role.id()
                    + ": assignment " + (current + count) + " exceeds capacity "
                    + capacity + " (a worker needs a completed building, docs/09)");
        }
        assignments.put(role.id(), current + count);
    }

    private void unassignWorker(Balance.RoleDef role, int count) {
        assignments.computeIfPresent(role.id(),
                (id, current) -> Math.max(0, current - count));
    }

    private void build(String buildingType) {
        Buildings.BuildingDef def = buildingsTable.type(buildingType);
        if (!isUnlockable(def)) {
            throw new IllegalArgumentException("building type not buildable yet"
                    + (def.requires() == null ? ": " + def.id()
                       : " (needs a completed " + def.requires() + "): " + def.id()));
        }
        for (Buildings.CostEntry entry : def.cost()) {
            long needed = Fixed.fromUnits(entry.amount());
            if (stockpile.get(entry.resource()) < needed) {
                throw new IllegalArgumentException("building " + def.id()
                        + ": needs " + entry.amount() + " " + entry.resource()
                        + ", stockpile holds "
                        + Fixed.unitsFloor(stockpile.get(entry.resource())));
            }
        }
        for (Buildings.CostEntry entry : def.cost()) {
            stockpile.put(entry.resource(),
                    stockpile.get(entry.resource()) - Fixed.fromUnits(entry.amount()));
        }
        String id = buildingType + "-" + String.format("%06d", nextBuildingSerial);
        nextBuildingSerial++;
        buildSites.add(new Building(id, buildingType, false, 0));
    }

    private boolean isUnlockable(Buildings.BuildingDef def) {
        if (def.isStartUnlocked()) {
            return true;
        }
        return def.requires() != null && hasCompletedInstance(def.requires());
    }

    private boolean hasCompletedInstance(String buildingType) {
        for (Building site : buildSites) {
            if (site.complete() && site.type().equals(buildingType)) {
                return true;
            }
        }
        return false;
    }

    private void cancelSite(String buildingId) {
        for (int i = 0; i < buildSites.size(); i++) {
            Building site = buildSites.get(i);
            if (site.id().equals(buildingId)) {
                if (site.complete()) {
                    throw new IllegalArgumentException(
                            "cannot cancel a completed building: " + buildingId);
                }
                for (Buildings.CostEntry entry : buildingsTable.type(site.type()).cost()) {
                    // Full refund (docs/09 OQ-2); may push stock above cap —
                    // production still clamps at the cap, nothing clamps here
                    stockpile.merge(entry.resource(),
                            Fixed.fromUnits(entry.amount()), Long::sum);
                }
                buildSites.remove(i);
                builders.remove(buildingId);
                events.add(new SimEvent.SiteCancelled(tick, buildingId));
                return;
            }
        }
        throw new IllegalArgumentException("no such building site: " + buildingId);
    }

    private void assignBuilders(String buildingId, int count) {
        Building site = findSite(buildingId);
        if (site.complete()) {
            throw new IllegalArgumentException(
                    "cannot assign builders to a completed building: " + buildingId);
        }
        builders.merge(buildingId, count, Integer::sum);
    }

    private void unassignBuilders(String buildingId, int count) {
        findSite(buildingId);
        builders.computeIfPresent(buildingId, (id, current) -> Math.max(0, current - count));
    }

    private Building findSite(String buildingId) {
        for (Building site : buildSites) {
            if (site.id().equals(buildingId)) {
                return site;
            }
        }
        throw new IllegalArgumentException("no such building site: " + buildingId);
    }

    // ------------------------------------------------------------------

    /** Base cap + completed capacity bonuses (fixed-point), cached. */
    private long effectiveCap(String resourceId) {
        if (capCache == null) {
            Map<String, Long> caps = new LinkedHashMap<>();
            for (Balance.ResourceDef def : balance.resources()) {
                caps.put(def.id(), def.capFixed());
            }
            for (Building site : buildSites) {
                if (site.complete()) {
                    Buildings.BuildingDef type = buildingsTable.type(site.type());
                    for (Map.Entry<String, Long> bonus : type.capacityBonus().entrySet()) {
                        caps.merge(bonus.getKey(),
                                type.capacityBonusFixed(bonus.getKey()), Long::sum);
                    }
                }
            }
            capCache = caps;
        }
        Long cap = capCache.get(resourceId);
        if (cap == null) {
            throw new IllegalArgumentException("unknown resource: " + resourceId);
        }
        return cap;
    }

    private int sourceRank(SimEvent event) {
        if (event instanceof SimEvent.RoleLevelUp levelUp) {
            return roleSourceRank(levelUp.roleId());
        }
        if (event instanceof SimEvent.StockpileFull stockpileFull) {
            return roleSourceRank(stockpileFull.resourceId()); // producing role
        }
        if (event instanceof SimEvent.BuildingFinished) {
            return Integer.MAX_VALUE / 2; // construction phase runs AFTER roles
        }
        return Integer.MAX_VALUE; // SiteCancelled (command-time, last resort)
    }

    private int roleSourceRank(String roleOrResourceId) {
        // delegate to Balance's table order via eventOrder's construction-time
        // map — thread the map through a field for a plain int return
        Integer index = eventRoleIndex.get(roleOrResourceId);
        return index == null ? Integer.MAX_VALUE : index;
    }

    private final Map<String, Integer> eventRoleIndex = new HashMap<>();

    private static int kindRank(SimEvent event) {
        if (event instanceof SimEvent.RoleLevelUp) {
            return 0;
        }
        if (event instanceof SimEvent.StockpileFull) {
            return 1;
        }
        if (event instanceof SimEvent.BuildingFinished) {
            return 2;
        }
        return 3; // SiteCancelled
    }
}
