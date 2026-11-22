package dev.goblintown.sim;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Placeholder roots for the determinism core (rules/03, categories 1–2).
 * These gates open with the first tick economy (phase-1 test priority 1).
 */
class DeterminismSmokeTest {

    @Test
    @Disabled("Land together with the first tick economy — not before. " +
              "See rules/03-testing.md §Phase-1 test priorities.")
    void simulationIsDeterministicAcrossTwoRuns() {
        // Two fresh runs from the same seed + command list must produce
        // byte-identical state hashes (two-run rule, rules/03).
        assertThat(true).isTrue(); // placeholder until SimEngine exists
    }

    @Test
    @Disabled("Offline catch-up must equal tick-by-tick play (rules/03 cat. 2); " +
              "8h equivalent must resolve in bounded time (H2).")
    void offlineCatchUpMatchesTickByTickPlay() {
        assertThat(true).isTrue();
    }
}
