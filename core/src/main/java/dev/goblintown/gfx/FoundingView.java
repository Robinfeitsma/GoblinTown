package dev.goblintown.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * The founding view (docs/10 screen graph): the first render task — the
 * isometric ground plane through the SHARED {@link IsoGrid} transform, with
 * the standing hero goblin rendered as a placeholder glyph. No input, no
 * state (M6: nothing here owns game truth); commands and the real founding
 * economy arrive with the founding phase.
 *
 * <p>Placeholder visuals: procedural tinted diamonds (no binary assets, H5
 * spirit — the scheduled art pass replaces the Pixmap glyphs).
 */
final class FoundingView implements Screen {

    static final int LOGICAL_WIDTH = 1280;
    static final int LOGICAL_HEIGHT = 720;
    static final int TILE_W = 128;
    static final int TILE_H = 64;

    private final IsoGrid iso =
            new IsoGrid(TILE_W, TILE_H, LOGICAL_WIDTH / 2f, LOGICAL_HEIGHT / 2f);
    private Viewport viewport;
    private SpriteBatch batch;
    private Texture groundA;
    private Texture groundB;
    private Texture hero;

    @Override
    public void show() {
        OrthographicCamera camera = new OrthographicCamera();
        camera.setToOrtho(true, LOGICAL_WIDTH, LOGICAL_HEIGHT);
        viewport = new FitViewport(LOGICAL_WIDTH, LOGICAL_HEIGHT, camera);
        batch = new SpriteBatch();
        groundA = diamond(new com.badlogic.gdx.graphics.Color(0.2f, 0.26f, 0.2f, 1f));
        groundB = diamond(new com.badlogic.gdx.graphics.Color(0.16f, 0.21f, 0.16f, 1f));
        hero = PlainTexture.solid(new com.badlogic.gdx.graphics.Color(0.5f, 0.8f, 0.4f, 1f));
    }

    @Override
    public void render(float delta) {
        viewport.apply();
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.03f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        for (int col = -3; col <= 3; col++) {
            for (int row = -3; row <= 3; row++) {
                Texture tile = (col + row) % 2 == 0 ? groundA : groundB;
                float x = iso.screenX(col, row);
                float y = iso.screenY(col, row);
                batch.draw(tile, x - iso.tileWidth() / 2f, y - iso.tileHeight() / 2f,
                        iso.tileWidth(), iso.tileHeight());
            }
        }
        batch.draw(hero, iso.screenX(0, 0) - 8, iso.screenY(0, 0) - 16, 16, 32);
        batch.end();
    }

    private static Texture diamond(com.badlogic.gdx.graphics.Color color) {
        Pixmap pixmap = new Pixmap(TILE_W, TILE_H, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        int cx = pixmap.getWidth() / 2;
        int cy = pixmap.getHeight() / 2;
        for (int dy = 0; dy < pixmap.getHeight(); dy++) {
            int half = (int) (cx * (1f - Math.abs((dy - cy) / (float) cy)));
            pixmap.drawRectangle(cx - Math.max(half, 1), dy, 2 * Math.max(half, 1), 1);
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        groundA.dispose();
        groundB.dispose();
        hero.dispose();
    }
}
