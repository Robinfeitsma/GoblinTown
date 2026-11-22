package dev.goblintown.gfx;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan-overlay fixture — DR-0001 validation task 4 (docs/10): draw a seeded
 * synthetic dungeon graph on the shared iso transform and hit-test the route
 * the player draws with clicks.
 *
 * <p>Route semantics (docs/10 OQ-2, click-to-append; docs/04 branch
 * fallbacks): the route implicitly STARTS at the start zone. Clicking a
 * graph node appends it if it is an edge from the route tail; clicking a
 * node already on the route truncates back to it; anything else is REJECTED
 * with countable feedback (first surfacing of the plan gates, no popups).
 *
 * <p>Headless-testable by construction: clicks and the route go through
 * {@link #click(float, float)} / {@link #route()} with zero engine state —
 * the on-GPU run (`--fixture overlay`) only proves pixels + frame budget.
 */
public final class PlanOverlayFixture implements ApplicationListener {

    public static final int LOGICAL_WIDTH = 1280;
    public static final int LOGICAL_HEIGHT = 720;
    static final int TILE_W = 128;
    static final int TILE_H = 64;
    /** Line snaps to the cursor within one tile of the route tail. */
    static final float CURSOR_SNAP_PX = TILE_W / 2f;

    static final long SEED = 20260211L;

    private final DungeonGraphFixture dungeon =
            new DungeonGraphFixture(SEED, DungeonGraphFixture.DEFAULT_DEPTH);
    private final IsoGrid iso =
            new IsoGrid(TILE_W, TILE_H, LOGICAL_WIDTH / 2f, LOGICAL_HEIGHT / 2f);
    private final List<String> route = new ArrayList<>();
    private float pointerX = iso.screenX(0, 0);
    private float pointerY = iso.screenY(0, 0);

    // observable feedback state (asserted headless, never simulated visuals)
    private int rejectedClicks;
    private int truncations;

    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private final java.util.Map<DungeonGraphFixture.NodeType, Texture> nodeTex =
            new java.util.EnumMap<>(DungeonGraphFixture.NodeType.class);
    private Texture routeDot;
    private Texture cursorDot;
    private OrthographicCamera camera;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(true, LOGICAL_WIDTH, LOGICAL_HEIGHT); // y DOWN = screen coords
        viewport = new FitViewport(LOGICAL_WIDTH, LOGICAL_HEIGHT, camera);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        shapes.setProjectionMatrix(camera.combined);

        for (DungeonGraphFixture.NodeType type : DungeonGraphFixture.NodeType.values()) {
            nodeTex.put(type, diamond(nodeColor(type)));
        }
        routeDot = filled(Color.WHITE, 6, 6);
        cursorDot = filled(new Color(1f, 1f, 0.4f, 0.9f), 5, 5);

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                return click(screenX, screenY);
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                pointerX = screenX;
                pointerY = screenY;
                return false;
            }
        });
    }

    /**
     * The one route mutation (docs/10): hit test → append / truncate /
     * reject. Returns whether the click landed on a node (rejections are
     * counted, never thrown — click spam must be harmless).
     */
    public boolean click(float x, float y) {
        // toCellNearest hit test in the SAME transform the render uses —
        // never a parallel cached-pixel model (docs/10 edge cases)
        DungeonGraphFixture.Node hit = dungeon.nodeAt(
                iso.nearestCol(x, y), iso.nearestRow(x, y));
        if (hit == null) {
            rejectedClicks++;
            return false;
        }
        if (hit.id().equals(dungeon.startId())) {
            // the start is the implicit route root (docs/10): landing on it
            // while a route exists means "clear the draw"
            if (!route.isEmpty()) {
                route.clear();
                truncations++;
            }
            return true;
        }
        if (route.contains(hit.id())) {
            // docs/04 branch fallbacks: go back to a zone you already have
            int upTo = route.indexOf(hit.id());
            while (route.size() > upTo + 1) {
                route.remove(route.size() - 1);
            }
            truncations++;
            return true;
        }
        String tail = route.isEmpty() ? dungeon.startId() : route.get(route.size() - 1);
        if (dungeon.isEdge(tail, hit.id())) {
            route.add(hit.id());
            return true;
        }
        rejectedClicks++;
        return false;
    }

    public List<String> route() {
        return List.copyOf(route);
    }

    public int rejectedClicks() {
        return rejectedClicks;
    }

    public int truncations() {
        return truncations;
    }

    public DungeonGraphFixture dungeon() {
        return dungeon;
    }

    public IsoGrid iso() {
        return iso;
    }

    @Override
    public void render() {
        viewport.apply();
        Gdx.gl.glClearColor(0.06f, 0.05f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // route line + snapped cursor segment (presentation floats)
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        String prev = dungeon.startId();
        for (String id : route) {
            shapes.line(iso.screenX(dungeon.byId(prev).col(), dungeon.byId(prev).row()),
                    iso.screenY(dungeon.byId(prev).col(), dungeon.byId(prev).row()),
                    iso.screenX(dungeon.byId(id).col(), dungeon.byId(id).row()),
                    iso.screenY(dungeon.byId(id).col(), dungeon.byId(id).row()));
            prev = id;
        }
        // last segment: tail → pointer, snapped to ≤ CURSOR_SNAP_PX
        float tailX = iso.screenX(dungeon.byId(prev).col(), dungeon.byId(prev).row());
        float tailY = iso.screenY(dungeon.byId(prev).col(), dungeon.byId(prev).row());
        float dx = pointerX - tailX;
        float dy = pointerY - tailY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > CURSOR_SNAP_PX) {
            dx = dx / len * CURSOR_SNAP_PX;
            dy = dy / len * CURSOR_SNAP_PX;
        }
        shapes.line(tailX, tailY, tailX + dx, tailY + dy);
        shapes.end();

        // nodes (route membership brightens them)
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (DungeonGraphFixture.Node node : dungeon.nodes()) {
            float x = iso.screenX(node.col(), node.row());
            float y = iso.screenY(node.col(), node.row());
            boolean onRoute = route.contains(node.id()) || node.id().equals(dungeon.startId());
            float scale = onRoute ? 1.15f : 1f;
            int w = Math.round(nodeTex.get(node.type()).getWidth() * scale);
            int h = Math.round(nodeTex.get(node.type()).getHeight() * scale);
            batch.draw(nodeTex.get(node.type()), x - w / 2f, y - h / 2f, w, h);
            if (route.contains(node.id())) {
                batch.draw(routeDot, x - 3, y - 3, 6, 6);
            }
        }
        batch.draw(cursorDot, pointerX - 2.5f, pointerY - 2.5f, 5, 5);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        // FitViewport letterboxes; the logical transform never shifts with
        // window size (docs/10 edge cases: hit tests go through the live
        // transform, which here stays in logical space)
        viewport.update(width, height);
    }

    private static Color nodeColor(DungeonGraphFixture.NodeType type) {
        return switch (type) {
            case ENCOUNTER -> new Color(0.75f, 0.2f, 0.2f, 1f);
            case ROOM -> new Color(0.55f, 0.4f, 0.25f, 1f);
            case SHRINE -> new Color(0.6f, 0.3f, 0.8f, 1f);
            case BEACON -> new Color(0.2f, 0.7f, 0.35f, 1f);
            case BOSS -> new Color(0.45f, 0.05f, 0.05f, 1f);
        };
    }

    /** A 2:1 diamond as two triangles, texture-space (px units PLACEHOLDER). */
    private static Texture diamond(Color color) {
        Pixmap pixmap = new Pixmap(TILE_W - 2, TILE_H - 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        int cx = pixmap.getWidth() / 2;
        int cy = pixmap.getHeight() / 2;
        for (int dy = 0; dy < pixmap.getHeight(); dy++) {
            int half = (int) (cx * (1f - Math.abs((dy - cy) / (float) cy)));
            pixmap.drawRectangle(cx - half, dy, 2 * Math.max(half, 1), 1);
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Texture filled(Color color, int w, int h) {
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        nodeTex.values().forEach(Texture::dispose);
        routeDot.dispose();
        cursorDot.dispose();
    }
}
