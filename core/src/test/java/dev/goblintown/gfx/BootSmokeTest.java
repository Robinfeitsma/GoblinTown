package dev.goblintown.gfx;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
        GL20 noOp = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
                new Class<?>[]{GL20.class}, BootSmokeTest::glInvocation);
        Gdx.gl = noOp;
        Gdx.gl20 = noOp;

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

    /**
     * The headless backend has no GL implementation; this shim answers the
     * Calls the render pipeline actually inspects. The shader-compile path is
     * load-bearing: SpriteBatch.createDefaultShader compiles the default
     * shader at boot and throws unless GL reports GL_COMPILE_STATUS /
     * GL_LINK_STATUS success, so the shim lies "compiled". Nothing is really
     * rendered — the shim only proves the render code path runs end to end;
     * visual correctness and the 60fps budget stay with the on-GPU fixture
     * run (`--fixture horde`).
     */
    private static Object glInvocation(Object proxy, Method method, Object[] args)
            throws Exception {
        String name = method.getName();
        switch (name) {
            case "glCreateShader":
            case "glCreateProgram":
            case "glGenBuffer":
            case "glGenTexture":
            case "glGenRenderbuffer":
            case "glGenFramebuffer":
                // non-zero object handles; anything <=0 reads as "not allocated"
                return Integer.valueOf(1);
            case "glGetShaderiv":            // GL_COMPILE_STATUS query
            case "glGetProgramiv":           // GL_LINK_STATUS / GL_VALIDATE_STATUS
                writeStatus(args);
                return null;
            case "glGetShaderInfoLog":
            case "glGetProgramInfoLog":
                return "";
            case "glGetString":
                return "1.0 headless-shim (no real GPU)";
            case "glCheckFramebufferStatus":
                return Integer.valueOf(GL20.GL_FRAMEBUFFER_COMPLETE);
            case "glGetError":
                return Integer.valueOf(GL20.GL_NO_ERROR);
            default:
                return defaultValue(method);
        }
    }

    /** Compile/link/validate succeed; zero uniforms/attributes to reflect. */
    private static void writeStatus(Object[] args) {
        if (args != null && args.length >= 3 && args[1] instanceof Integer pname) {
            boolean zero = pname == GL20.GL_ACTIVE_UNIFORMS
                    || pname == GL20.GL_ACTIVE_ATTRIBUTES;
            java.nio.IntBuffer buf = (java.nio.IntBuffer) args[2];
            buf.put(0, zero ? 0 : 1);
        }
    }

    /** GL default for primitives; null for everything else. */
    private static Object defaultValue(Method method) {
        Class<?> r = method.getReturnType();
        if (!r.isPrimitive()) {
            return null;
        }
        if (r == boolean.class) return Boolean.FALSE;
        if (r == int.class) return Integer.valueOf(0);
        if (r == long.class) return Long.valueOf(0L);
        if (r == float.class) return Float.valueOf(0f);
        if (r == double.class) return Double.valueOf(0d);
        if (r == short.class) return Short.valueOf((short) 0);
        if (r == byte.class) return Byte.valueOf((byte) 0);
        if (r == char.class) return Character.valueOf((char) 0);
        return null;
    }
}
