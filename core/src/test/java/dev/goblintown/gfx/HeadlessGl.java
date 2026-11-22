package dev.goblintown.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

/**
 * Headless-GL shim shared by every render test (rules/03 cat. 6). The headless
 * backend ships a NULL GL20; this dynamic proxy stands in: no-op for state
 * calls, and it "compiles" the default SpriteBatch/ShapeRenderer shaders so
 * the render pipeline runs end to end. Nothing is really rendered — visual
 * correctness and the 60fps budget stay with the on-GPU fixture runs
 * (`--fixture horde` / `--fixture overlay` via :lwjgl3:run, DR-0001).
 */
final class HeadlessGl {

    private static GL20 originalGl;

    private HeadlessGl() {
    }

    /** Swaps in the shim; pairs with {@link #restore()}. */
    static void install() {
        originalGl = Gdx.gl;
        GL20 noOp = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
                new Class<?>[]{GL20.class}, HeadlessGl::glInvocation);
        Gdx.gl = noOp;
        Gdx.gl20 = noOp;
    }

    static void restore() {
        Gdx.gl = originalGl;
        Gdx.gl20 = originalGl;
    }

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
            IntBuffer buf = (IntBuffer) args[2];
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
