package dev.goblintown.sim;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixed-point convention itself (DR-0003) — the arithmetic the whole sim
 * ring sits on. If any of these fail, nothing above them can be trusted.
 */
class FixedTest {

    @Test
    void oneUnitIsExactlyOneLeftShift() {
        assertThat(Fixed.ONE).isEqualTo(0x1_0000_0000L);
        assertThat(Fixed.fromUnits(0)).isEqualTo(0);
        assertThat(Fixed.fromUnits(37)).isEqualTo(37L << 32);
        assertThat(Fixed.unitsFloor(Fixed.fromUnits(37))).isEqualTo(37);
    }

    @Test
    void multiplicationIsExactAndIntegerOnly() {
        // (0.5)² = 0.25 — exact in Q32.32
        long half = Fixed.ONE >> 1;
        assertThat(Fixed.mul(half, half)).isEqualTo(Fixed.ONE >> 2);
        assertThat(Fixed.mul(Fixed.ONE, Fixed.fromUnits(9))).isEqualTo(Fixed.fromUnits(9));

        // large values: precision lives in the 128-bit intermediate.
        // Range note (DR-0003): a Q32.32 value represents at most ~2.15e9
        // whole units, so the square of 40,000 units (1.6e9) fits exactly.
        long big = Fixed.fromUnits(40_000L);
        assertThat(Fixed.mul(big, big)).isEqualTo(Fixed.fromUnits(1_600_000_000L));
    }

    @Test
    void ceilDivMatchesHandMath() {
        assertThat(Fixed.ceilDiv(10, 3)).isEqualTo(4);
        assertThat(Fixed.ceilDiv(9, 3)).isEqualTo(3);
        assertThat(Fixed.ceilDiv(1, 7)).isEqualTo(1);
        assertThat(Fixed.ceilDiv(0, 7)).isEqualTo(0);
    }
}
