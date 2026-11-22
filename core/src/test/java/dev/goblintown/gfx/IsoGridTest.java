package dev.goblintown.gfx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared isometric transform (docs/10): exact round-trips on integer
 * cells and known screen positions — the invariant every hit test leans on
 * (rules/03 cat. 5: UI hit-testing is tested; pure math, no GL needed).
 */
class IsoGridTest {

    private final IsoGrid iso = new IsoGrid(128, 64, 640, 360);

    @Test
    void roundTripIsExactOnIntegerCells() {
        for (int col = -20; col <= 20; col++) {
            for (int row = -20; row <= 20; row++) {
                float x = iso.screenX(col, row);
                float y = iso.screenY(col, row);
                assertThat(iso.nearestCol(x, y))
                        .as("col round-trip at (%d,%d)", col, row)
                        .isEqualTo(col);
                assertThat(iso.nearestRow(x, y))
                        .as("row round-trip at (%d,%d)", col, row)
                        .isEqualTo(row);
            }
        }
    }

    @Test
    void knownScreenPositions() {
        IsoGrid centered = new IsoGrid(128, 64, 0, 0);
        // (0,0) is the origin; (1,1) steps straight down half a tile height;
        // (2,0) → one tile right and one tile-halves down
        assertThat(centered.screenX(0, 0)).isZero();
        assertThat(centered.screenY(0, 0)).isZero();
        assertThat(centered.screenX(1, 1)).isEqualTo(0f);
        assertThat(centered.screenY(1, 1)).isEqualTo(64f);
        assertThat(centered.screenX(2, 0)).isEqualTo(128f);
        assertThat(centered.screenY(2, 0)).isEqualTo(64f);
    }

    @Test
    void rebaseMovesTheOriginOnly() {
        iso.rebase(100, 50);
        assertThat(iso.originX()).isEqualTo(100);
        assertThat(iso.originY()).isEqualTo(50);
        assertThat(iso.tileWidth()).isEqualTo(128);
        assertThat(iso.screenX(0, 0)).isEqualTo(100);
    }

    @Test
    void rejectsImpossibleTileSizes() {
        try {
            new IsoGrid(0, 64, 0, 0);
            throw new AssertionError("expected IAE");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("positive");
        }
    }
}
