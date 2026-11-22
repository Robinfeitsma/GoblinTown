package dev.goblintown.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The determinism core (rules/03, categories 1–2) — the tests the whole
 * project stands on. A flaky hash diff here is a P0 bug, never a retry.
 */
class DeterminismSmokeTest {

    /**
     * Golden seeds (rules/03 §Determinism test conventions): every merged PR
     * extends the list; each test loops all of them. Falls back to the
     * default seed if the file is empty (so a fresh checkout is never silently
     * skipping the core).
     */
    static List<Long> goldenSeeds() throws IOException {
        List<Long> seeds = new ArrayList<>();
        InputStream in = DeterminismSmokeTest.class.getResourceAsStream("/golden-seeds.txt");
        if (in != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        seeds.add(Long.parseLong(trimmed));
                    }
                }
            }
        }
        if (seeds.isEmpty()) {
            seeds.add(TestSupport.SEED);
        }
        return seeds;
    }

    /** Two-run rule (rules/03): two fresh engines, same seed, same command
     *  list → byte-identical state hashes. Loops every golden seed. */
    @ParameterizedTest
    @MethodSource("goldenSeeds")
    void simulationIsDeterministicAcrossTwoRuns(long seed) {
        String first = runScenarioAndHash(seed);
        String second = runScenarioAndHash(seed);
        assertThat(second)
                .as("two-run rule violated for seed %d — P0 bug, do not retry", seed)
                .isEqualTo(first);
    }

    /**
     * Offline catch-up must equal tick-by-tick play (rules/03 category 2,
     * exact tolerance), including every event's position in the timeline.
     * Uses the 8h-absence length from H2 (60 Hz × 8h = 1,728,000 ticks) —
     * which the closed-form path must absorb in bounded time (no per-tick
     * loop; enforced by construction, verified by equality here).
     */
    @Test
    void offlineCatchUpMatchesTickByTickPlay() throws IOException {
        int eightHoursOfTicks = Balance.TICKS_PER_SECOND * 60 * 60 * 8;
        for (long seed : goldenSeeds()) {
            // Live play: commands at step boundaries, then long steady stretch.
            SimEngine live = TestSupport.freshTown(seed);
            long liveLastStepTick = 0;
            for (TestSupport.ScenarioStep step : TestSupport.SCENARIO) {
                live.execute(step.command());
                live.tick(step.ticks());
                liveLastStepTick += step.ticks();
            }
            live.tick(eightHoursOfTicks);
            GameState liveEnd = live.snapshot();

            // Offline catch-up: same start + same commands; the whole
            // steady stretch resolved in ONE closed-form catch-up
            // (~1.73M ticks — H2's 8h return).
            SimEngine offline = TestSupport.freshTown(seed);
            long offlineAfterSteps = 0;
            for (TestSupport.ScenarioStep step : TestSupport.SCENARIO) {
                offline.execute(step.command());
                offline.tick(step.ticks());
                offlineAfterSteps += step.ticks();
            }
            offline.catchUp(eightHoursOfTicks);
            GameState offlineEnd = offline.snapshot();

            assertThat(SaveCodec.stateHash(offlineEnd))
                    .as("catch-up must equal tick-by-tick play (seed %d)", seed)
                    .isEqualTo(SaveCodec.stateHash(liveEnd));

            // And the merged world must keep agreeing afterwards: resume
            // live play from BOTH snapshots with identical commands.
            // (gatherer sits at 2 after the scenario; +2 fills the camp's
            // four slots to capacity.)
            int tailTicks = 5_000;
            live.execute(new dev.goblintown.sim.command.Command.AssignWorkers("gatherer", 2));
            live.tick(tailTicks);
            offline.execute(new dev.goblintown.sim.command.Command.AssignWorkers("gatherer", 2));
            offline.tick(tailTicks);
            assertThat(SaveCodec.stateHash(offline.snapshot()))
                    .as("post-catch-up resume must equal live play (seed %d)", seed)
                    .isEqualTo(SaveCodec.stateHash(live.snapshot()));

            // sanity: the scenario really did span the intended horizon
            assertThat(liveEnd.tick()).isEqualTo(liveLastStepTick + eightHoursOfTicks);
        }
    }

    private String runScenarioAndHash(long seed) {
        SimEngine engine = TestSupport.freshTown(seed);
        for (TestSupport.ScenarioStep step : TestSupport.SCENARIO) {
            engine.execute(step.command());
            engine.tick(step.ticks());
        }
        return SaveCodec.stateHash(engine.snapshot());
    }
}
