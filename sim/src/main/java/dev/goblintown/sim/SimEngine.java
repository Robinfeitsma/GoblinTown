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
 * The Phase-1 tick economy engine (docs/03-idle-loop.md §Production model —
 * skeleton subset): one command path in, deterministic ticks and a
 * closed-form offline catch-up out.
 *
 * <p>Contract (H1/H2, rules/02 §2–3):
 * <ul>
 *   <li>{@link #tick} advances {code n} ticks one at a time.</li>
 *   <li>{@link #catchUp} advances {code n} ticks in closed form — O(roles +
 *       events), never O(ticks) — and produces a state BYTE-IDENTICAL to
 *       sequential {@link #tick} (rules/03 category 2: exact tolerance).
 *       Valid for Phase-1 production (linear rates; level-up and cap
 *       boundaries solved exactly, DR-0003).</li>
 *   <li>Same initial state + same ordered commands → same result, bit for
 *       bit (H1).</li>
 * </ul>
 *
 * <p>The engine owns its working state as plain data (rules/02 §1: no hidden
 * rules in the data classes); {@link #snapshot()} hands out immutable
 * {@link GameState} records — the only state shape that ever crosses a
 * boundary (M3: save, renderers, headless output).
 *
 * <p>Tick order inside one tick (binding for determinism): tick counter
 * increments first, then roles run in {@link Balance#roles()} order; within
 * a role, XP is accrued (and level-ups fired) before production/clamping.
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

    /**
     * The canonical event order of {@code tickOnce}: by tick, then the
     * emitting role's position in the balance role list (a resource's
     * StockpileFull is emitted by the role that produces it — the resource id
     * maps through {@code resourceProducer}), then level-ups before
     * production events within one role. Built per engine; this must remain
     * EXACTLY the order the tickOnce role loop emits, or catch-up stars
     * diverging from live play (H1).
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

    /** Factory: fresh game from a balance table + seed (seed → rngState). */
    public static SimEngine newGame(Balance balance, long seed) {
        return new SimEngine(balance, GameState.initial(balance, seed));
    }

    public SimEngine(Balance balance, GameState state) {
        this.balance = balance;
        // role position for RoleLevelUp; resourceId → producing role's
        // position for StockpileFull (both = position in the tickOnce loop)
        Map<String, Integer> eventRoleOrder = new HashMap<>();
        List<Balance.RoleDef> defs = balance.roles();
        for (int i = 0; i < defs.size(); i++) {
            eventRoleOrder.put(defs.get(i).id(), i);
            if (defs.get(i).produces() != null) {
                eventRoleOrder.putIfAbsent(defs.get(i).produces(), i);
            }
        }
        this.eventOrder = Comparator
                .comparingLong(SimEvent::tick)
                .thenComparingInt(e -> roleOrderOf(eventRoleOrder, e))
                .thenComparingInt(SimEngine::kindRank);
        this.tick = state.tick();
        this.stockpile = new LinkedHashMap<>(state.stockpile());
        this.assignments = new LinkedHashMap<>(state.assignments());
        this.roleXp = new LinkedHashMap<>(state.roleXp());
        this.roleLevel = new LinkedHashMap<>(state.roleLevel());
        this.rngState = state.rngState();
        this.events = new ArrayList<>(state.events());
    }

    /** Current state as an immutable snapshot (saved, hashed, rendered). */
    public GameState snapshot() {
        return new GameState(tick, new LinkedHashMap<>(stockpile), new LinkedHashMap<>(assignments),
                new LinkedHashMap<>(roleXp), new LinkedHashMap<>(roleLevel), rngState,
                new ArrayList<>(events));
    }

    /** Applies a command at the current tick (command list drives H1).
     *  instanceof dispatch, not pattern switch: we compile to release 17
     *  (DR-0001), where switch patterns don't exist yet. */
    public void execute(Command command) {
        if (command instanceof Command.AssignWorkers assign) {
            assign(balance.role(assign.roleId()), assign.count());
        } else if (command instanceof Command.UnassignWorkers unassign) {
            unassign(balance.role(unassign.roleId()), unassign.count());
        } else {
            throw new IllegalArgumentException("unknown command: " + command);
        }
    }

    /** Current tick (the only time in sim; wall-clock never enters, H1). */
    public long tick() {
        return tick;
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
        // Per role: exact production, XP and event boundaries. Roles do not
        // interact in Phase-1 production (each produces one resource, nothing
        // consumes), so per-role closed forms compose.
        for (Balance.RoleDef role : balance.roles()) {
            int workers = assignments.get(role.id());
            if (workers <= 0) {
                continue;
            }
            catchUpRole(role, workers, startTick, n);
        }
        // tickOnce emits events strictly chronologically (ties broken by role
        // order, level-ups before production within a role — the exact order
        // its role loop produces). The closed-form path solved roles one at a
        // time, so restore the tickOnce order here; must be identical (H1).
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
        long cap = resource.capFixed();
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

    // ------------------------------------------------------------------
    // closed-form catch-up path (must mirror tickOnce exactly — rules/03
    // category 2 gates this with exact-equality tests)
    // ------------------------------------------------------------------

    private void catchUpRole(Balance.RoleDef role, int workers, long startTick, int ticks) {

        // XP: linear growth → solve level boundaries exactly. `consumed`
        // ticks have "elapsed" when a threshold is first met, so the event
        // tick matches tickOnce's (which counts the tick it fired in).
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
                    // tickOnce increments the tick counter before XP accrues,
                    // so the crossing tick is startTick + consumed.
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

        // Production: clamp at cap exactly where tickOnce clamps, with the
        // same single StockpileFull transition event. A zero/absent producer
        // contributes nothing (validation may hold such placeholder roles).
        if (role.produces() != null) {
            Balance.ResourceDef resource = balance.resource(role.produces());
            long cap = resource.capFixed();
            long amount = stockpile.get(resource.id());
            long gainPerTickProd = (long) workers * role.amountPerTickFixed();
            if (amount < cap && gainPerTickProd > 0) {
                long fillTick = Fixed.ceilDiv(cap - amount, gainPerTickProd);
                if (fillTick <= ticks) {
                    events.add(new SimEvent.StockpileFull(startTick + fillTick, resource.id()));
                    stockpile.put(resource.id(), cap);
                } else {
                    stockpile.put(resource.id(), amount + gainPerTickProd * ticks);
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private void assign(Balance.RoleDef role, int count) {
        assignments.merge(role.id(), count, Integer::sum);
    }

    private void unassign(Balance.RoleDef role, int count) {
        assignments.computeIfPresent(role.id(),
                (id, current) -> Math.max(0, current - count));
    }

    private static int roleOrderOf(Map<String, Integer> eventRoleOrder, SimEvent event) {
        String key;
        if (event instanceof SimEvent.RoleLevelUp levelUp) {
            key = levelUp.roleId();
        } else if (event instanceof SimEvent.StockpileFull stockpileFull) {
            key = stockpileFull.resourceId(); // maps to its producing role
        } else {
            key = null;
        }
        Integer index = key == null ? null : eventRoleOrder.get(key);
        return index == null ? Integer.MAX_VALUE : index;
    }

    private static int kindRank(SimEvent event) {
        if (event instanceof SimEvent.RoleLevelUp) {
            return 0;
        }
        return 1; // StockpileFull (and any future production-kind event)
    }
}
