package dev.goblintown.gfx;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot smoke test (rules/03 category 6): the render layer boots and reaches
 * real frames without a window. HeadlessApplication drives create()/render()
 * on its own thread — we delegate through a recording wrapper and AWAIT a
 * render count (never race or assert speed; the 60fps budget itself needs a
 * real GPU and stays a user-run step:
 * `./gradlew :lwjgl3:run --args="--fixture horde"`, DR-0001).
 *
 * <p>Headless plumbing details this test owns:
 * <ul>
 *   <li>gdx natives must be extracted BEFORE anything builds a
 *       SpriteBatch/Mesh — the headless backend does not do it;</li>
 *   <li>the headless backend ships a NULL GL20 — a dynamic-proxy shim over
 *       the pure interface stands in: no-op for state calls, and it
 *       "compiles" the default SpriteBatch shader so the render pipeline
 *       runs end to end.</li>
 * </ul>
 */
class BootSmokeTest {

    private static GL20 originalGl;
    private static GL20 originalGl20;

    @Test
    void hordeFixtureBootsAndRendersFramesHeadlessly() throws InterruptedException {
        originalGl = Gdx.gl;
        originalGl20 = Gdx.gl20;
        GdxNativesLoader.load();
        HeadlessGl.install();

        HordeRenderFixture fixture = new HordeRenderFixture();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger rendered = new AtomicInteger();

        ApplicationListener recorder = new ApplicationListener() {
            @Override
            public void create() {
                try {
                    fixture.create();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }

            @Override
            public void render() {
                try {
                    fixture.render();
                    rendered.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }

            @Override
            public void resize(int width, int height) {
            }

            @Override
            public void pause() {
            }

            @Override
            public void resume() {
            }

            @Override
            public void dispose() {
            }
        };

        HeadlessApplication app = new HeadlessApplication(recorder,
                new HeadlessApplicationConfiguration());
        try {
            // await 100 renders (fixture logs a stats line per ~1s of them);
            // liberal timeout so we measure nothing about machine speed
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (rendered.get() < 100 && failure.get() == null
                    && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
        } finally {
            app.exit();
        }

        assertThat(failure.get())
                .as("fixture must boot and render headlessly without throwing")
                .isNull();
        assertThat(rendered.get())
                .as("the render loop must have actually run frames")
                .isGreaterThanOrEqualTo(100);
    }

    @AfterAll
    static void restoreGl() {
        Gdx.gl = originalGl;
        Gdx.gl20 = originalGl20;
    }
}
