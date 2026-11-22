package dev.goblintown.gfx;

/**
 * The ONE isometric transform (docs/10 §Design): 2:1 diamond tile, shared by
 * the overlay fixture, the founding view, and later the town HUD — nobody
 * re-derives their own projection math.
 *
 * <pre>
 *   screenX = originX + (col − row) · tileW/2
 *   screenY = originY + (col + row) · tileH/2      (y down, libGDX screen space)
 * </pre>
 *
 * Presentation-only floats: values here must never re-enter the sim ring
 * (DR-0003 scope). Tested against its own inverse with exact round-trips on
 * integer grid cells (rules/03 cat. 5: UI hit-testing is tested).
 */
public final class IsoGrid {

    private final int tileW;
    private final int tileH;
    private float originX;
    private float originY;

    public IsoGrid(int tileW, int tileH, float originX, float originY) {
        if (tileW <= 0 || tileH <= 0) {
            throw new IllegalArgumentException("tile size must be positive");
        }
        this.tileW = tileW;
        this.tileH = tileH;
        this.originX = originX;
        this.originY = originY;
    }

    /** Screen position (px, y down) of the CENTER of a grid cell. */
    public float screenX(int col, int row) {
        return originX + (col - row) * (tileW / 2f);
    }

    public float screenY(int col, int row) {
        return originY + (col + row) * (tileH / 2f);
    }

    /** The grid cell at a screen position (nearest-cell hit test, docs/10). */
    public int nearestCol(float x, float y) {
        float a = (x - originX) / (tileW / 2f);
        float b = (y - originY) / (tileH / 2f);
        return Math.round((a + b) / 2f);
    }

    public int nearestRow(float x, float y) {
        float a = (x - originX) / (tileW / 2f);
        float b = (y - originY) / (tileH / 2f);
        return Math.round((b - a) / 2f);
    }

    /** Viewport letterboxing: re-bases only the offset, never the tile size. */
    public void rebase(float newOriginX, float newOriginY) {
        this.originX = newOriginX;
        this.originY = newOriginY;
    }

    public int tileWidth() {
        return tileW;
    }

    public int tileHeight() {
        return tileH;
    }

    public float originX() {
        return originX;
    }

    public float originY() {
        return originY;
    }
}
