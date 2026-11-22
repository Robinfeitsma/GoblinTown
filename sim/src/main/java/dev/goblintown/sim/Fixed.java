package dev.goblintown.sim;

/**
 * The fixed-point convention for the entire {@code sim} ring — Q32.32 in
 * {@code int64}, fixed by DR-0003. One whole unit is {@link #ONE}. This class
 * is the ONLY place the shift width exists; changing the convention is a save
 * migration (DR-0003 §Consequences), never a logic edit.
 *
 * <p>Determinism rules (H1, DR-0001): only integer {@code + - * /} and shifts
 * appear here. {@link #mul} is exact via a 128-bit intermediate
 * ({@link Math#multiplyHigh} + shifts — integer-only, JDK 9+). Truncation
 * happens exclusively on explicitly documented division/conversion paths and
 * is part of the sim contract (identical on every JVM).
 *
 * <p>Integer-unit values (worker counts, levels, tick numbers) are plain
 * {@code int}/{@code long}, NOT fixed. Fixed-point is used only where the
 * value can be fractional (rates, XP pools, future aura math).
 */
public final class Fixed {

    /** Fraction bits. Referenced nowhere else in the code base (DR-0003). */
    public static final int SHIFT = 32;

    /** One whole unit in fixed-point representation: 0x1_0000_0000. */
    public static final long ONE = 1L << SHIFT;

    private Fixed() {
        // constants + static helpers only
    }

    /** Whole units → fixed. Exact for the ranges balance tables may hold
     *  (the Balance loader rejects values that would overflow). */
    public static long fromUnits(long units) {
        return Math.multiplyExact(units, ONE);
    }

    /** Fixed → whole units, truncating toward zero (documented lossy path). */
    public static long unitsFloor(long fixed) {
        return fixed >> SHIFT;
    }

    /** Exact fixed × fixed: 128-bit intermediate, then truncate the fraction
     *  part. Integer-only; identical on every JVM (H1). */
    public static long mul(long a, long b) {
        // (a*b) is a 128-bit signed product; >> SHIFT arithmetic.
        long hi = Math.multiplyHigh(a, b);
        long lo = a * b;
        // Full 128-bit >> SHIFT with signed semantics: take hi shifted left
        // by (64-SHIFT) and or in the unsigned top SHIFT bits of lo.
        return (hi << SHIFT) | (lo >>> (64 - SHIFT));
        // Note: `|` and `+` agree here because the shifted-out high bits of
        // (lo >>> (64-SHIFT)) above bit 32 are zero.
    }

    /** ceil(a / b) for positive a, b — integer-only. Used both by the
     *  tick-by-tick and the closed-form catch-up paths, so both derive the
     *  same boundary ticks (H1). */
    public static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
