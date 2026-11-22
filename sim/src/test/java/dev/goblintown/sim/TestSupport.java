package dev.goblintown.sim;

import dev.goblintown.sim.command.Command;

import java.io.InputStream;
import java.util.List;

/**
 * Shared helpers for the sim test suite: the real balance table (loaded from
 * the repo-root {@code data/} directory via the test classpath — no copied
 * fixtures) and the standard Phase-1 command list used by determinism tests.
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

    static SimEngine newGame(long seed) {
        return SimEngine.newGame(balance(), seed);
    }

    /**
     * The standard Phase-1 scenario the determinism tests replay: assign,
     * advance, re-shuffle, advance, fill the stockpile, advance past it.
     * Kept deliberately boring — the interesting part is that both runs must
     * produce identical hashes.
     */
    static final List<ScenarioStep> SCENARIO = List.of(
            new ScenarioStep(new Command.AssignWorkers("gatherer", 4), 600),
            new ScenarioStep(new Command.AssignWorkers("miner", 3), 600),
            new ScenarioStep(new Command.UnassignWorkers("gatherer", 2), 600),
            new ScenarioStep(new Command.AssignWorkers("miner", 20), 60_000)
    );

    record ScenarioStep(Command command, int ticks) {
    }

    private TestSupport() {
    }
}
