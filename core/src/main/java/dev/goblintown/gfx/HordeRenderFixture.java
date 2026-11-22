package dev.goblintown.gfx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Horde render fixture (DR-0001 validation task 3, H6): 300+ animated
 * sprites through ONE {@link SpriteBatch}, frame budget measured and logged.
 * This is an acceptance instrument, not game code — it proves the desktop
 * backend keeps the 60fps budget at horde scale before any real renderer
 * exists.
 *
 * <p>Run it on dev hardware:
 * <pre>./gradlew :lwjgl3:run --args="--fixture horde"</pre>
 * It logs one stats line per second and exits on ESC or window close.
 *
 * <p>Rules-compliant placement: rendering belongs to core; it reads NOTHING
 * from the sim (rules/02: core must never contain game rules/balance) and it
 * may freely use wall-clock — the no-clock rule binds the sim ring only.
 */
public final class HordeRenderFixture extends ApplicationAdapter {

    /** Phase-1 horde floor from H6 ("300+ animated sprites"). */
    static final int SPRITE_COUNT = 320;

    private SpriteBatch batch;
    private Texture sprite;
    private final float[] x = new float[SPRITE_COUNT];
    private final float[] y = new float[SPRITE_COUNT];
    private final float[] vx = new float[SPRITE_COUNT];
    private final float[] vy = new float[SPRITE_COUNT];

    private long frameStatsStart;
    private long framesInWindow;
    private long worstFrameNsInWindow;
    private long lastFrameStart;

    @Override
    public void create() {
        batch = new SpriteBatch();
        Pixmap pixmap = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        pixmap.setColor(1, 1, 1, 1);
        pixmap.fill();
        sprite = new Texture(pixmap);
        pixmap.dispose();

        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        for (int i = 0; i < SPRITE_COUNT; i++) {
            x[i] = (float) (Math.random() * width);
            y[i] = (float) (Math.random() * height);
            vx[i] = 30f + 240f * (float) Math.random();
            vy[i] = 30f + 240f * (float) Math.random();
        }
        frameStatsStart = System.nanoTime();
        lastFrameStart = frameStatsStart;
        Gdx.app.log("horde-fixture", "created: " + SPRITE_COUNT
                + " animated sprites @ " + width + "x" + height);
    }

    @Override
    public void render() {
        long now = System.nanoTime();
        long frameNs = now - lastFrameStart;
        lastFrameStart = now;

        float delta = Gdx.graphics.getDeltaTime();
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        for (int i = 0; i < SPRITE_COUNT; i++) {
            x[i] += vx[i] * delta;
            y[i] += vy[i] * delta;
            if (x[i] < 0 || x[i] > width - 8) {
                vx[i] = -vx[i];
            }
            if (y[i] < 0 || y[i] > height - 8) {
                vy[i] = -vy[i];
            }
        }

        Gdx.gl.glClearColor(0.08f, 0.07f, 0.06f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        for (int i = 0; i < SPRITE_COUNT; i++) {
            // bob each sprite vertically — the "animation" under test
            batch.draw(sprite, x[i], y[i] + 2f * (float) Math.sin(now * 1e-6 + i));
        }
        batch.end();

        framesInWindow++;
        worstFrameNsInWindow = Math.max(worstFrameNsInWindow, frameNs);
        if (now - frameStatsStart >= 1_000_000_000L) {
            double windowS = (now - frameStatsStart) / 1e9;
            double avgMs = windowS * 1000 / framesInWindow;
            double worstMs = worstFrameNsInWindow / 1e6;
            Gdx.app.log("horde-fixture", String.format(
                    "%d sprites: %.0f fps (frame budget 16.7ms), avg %.2f ms, worst %.2f ms — %s",
                    SPRITE_COUNT, framesInWindow / windowS, avgMs, worstMs,
                    avgMs <= 16.7 ? "OK" : "BUDGET MISSED"));
            frameStatsStart = now;
            framesInWindow = 0;
            worstFrameNsInWindow = 0;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        sprite.dispose();
    }
}
