package dev.goblintown.sim;

import dev.goblintown.sim.command.Command;

import java.io.InputStream;
import java.util.List;

/**
 * Shared helpers for the sim test suite: the real tables (loaded from the
 * repo-root {@code data/} directory via the test classpath — no copied
 * fixtures) and standard engines.
 *
 * <p>Two engine flavors on purpose (rules/03: expected values derive from
 * the design docs *and* the data table — same source of truth everywhere):
 * <ul>
 *   <li>{@link #newGame(long)} — role-only tables ({@code Buildings.none()}):
 *       free assignment, Phase-1 linear economy. The classic tick-economy
 *       math tests run here.</li>
 *   <li>{@link #townGame(long)} — the REAL founding flow with the real
 *       building table: wood camp built + staffed, construction complete.
 *       Building tests run here and in {@code BuildingSimTest}.</li>
 * </ul>
 */
final class TestSupport {

    /** Default seed used when no golden seed list applies. */
    static final long SEED = 20260211L;

    static Balance balance() {
        InputStream in = TestSupport.class.getResourceAsStream("/economy.json");
        if (in == null) {
            throw new IllegalStateException("data/economy.json not on test classpath "
                    + "(see build.gradle: sim test resources include ../data)");
        }
        return Balance.fromJson(in);
    }

    static Buildings buildings() {
        InputStream in = TestSupport.class.getResourceAsStream("/buildings.json");
        if (in == null) {
            throw new IllegalStateException("data/buildings.json not on test classpath "
                    + "(see build.gradle: sim test resources include ../data)");
        }
        return Buildings.fromJson(in, balance());
    }

    /** Role-only engine: unlimited assignment capacity (Phase-1 semantics). */
    static SimEngine newGame(long seed) {
        return SimEngine.newGame(balance(), Buildings.none(), seed);
    }

    /** Real tables, fresh founding (empty town, foundingSupplies stock). */
    static SimEngine freshTown(long seed) {
        return SimEngine.newGame(balance(), buildings(), seed);
    }

    /**
     * The standard founding flow with the REAL tables: wood camp built (its
     * 450-wood founding-supply cost deducted), staffed with 8 builders,
     * ticked to completion (progressCost 4500 / 8 = 562.5 → 563 ticks),
     * ready to receive gatherers (capacity 4). Deterministic: 563 is exact.
     */
    static final int CAMP_COMPLETION_TICK = 563;
    static final String CAMP_ID = "wood-camp-000001";

    static SimEngine townGame(long seed) {
        SimEngine engine = freshTown(seed);
        engine.execute(new Command.Build("wood-camp"));
        engine.execute(new Command.AssignBuilders(CAMP_ID, 8));
        engine.tick(CAMP_COMPLETION_TICK);
        return engine;
    }

    /**
     * The standard determinism scenario the golden-seed tests replay:
     * build, staff, complete, assign, shuffle, extend — the role economy
     * (production, level-ups, caps) plus a site cancellation mid-flow, all
     * through the command boundary the H1 contract covers.
     */
    static final List<ScenarioStep> SCENARIO = List.of(
            new ScenarioStep(new Command.Build("wood-camp"), 0),
            new ScenarioStep(new Command.AssignBuilders(CAMP_ID, 8), CAMP_COMPLETION_TICK),
            new ScenarioStep(new Command.AssignWorkers("gatherer", 4), 12_000),
            new ScenarioStep(new Command.Build("stone-quarry"), 0),
            new ScenarioStep(new Command.AssignBuilders("stone-quarry-000002", 4), 1_125),
            new ScenarioStep(new Command.AssignWorkers("miner", 4), 12_000),
            new ScenarioStep(new Command.Build("food-larder"), 0),
            new ScenarioStep(new Command.AssignBuilders("food-larder-000003", 5), 200),
            new ScenarioStep(new Command.CancelSite("food-larder-000003"), 6_000),
            new ScenarioStep(new Command.UnassignWorkers("gatherer", 2), 600)
    );

    record ScenarioStep(Command command, int ticks) {
    }

    private TestSupport() {
    }
}
