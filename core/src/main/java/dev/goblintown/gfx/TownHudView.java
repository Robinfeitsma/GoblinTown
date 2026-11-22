package dev.goblintown.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.goblintown.sim.Fixed;
import dev.goblintown.sim.GameState;

/**
 * Town-HUD skeleton (docs/10, OQ-3: scene2d widgets). Reads a
 * {@link GameState} snapshot handed to it — the screen owns no game truth,
 * issues no commands yet (commands arrive with the construction UI phase).
 */
final class TownHudView implements com.badlogic.gdx.Screen {

    private Stage stage;
    private Label stockLine;
    private Label tierLine;

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Label.LabelStyle style = new Label.LabelStyle(new com.badlogic.gdx.graphics.g2d.BitmapFont(),
                Color.WHITE);
        stockLine = new Label("stock  —", style);
        tierLine = new Label("tier   0", style);
        stockLine.setPosition(16, 16);
        tierLine.setPosition(16, 48);
        stage.addActor(stockLine);
        stage.addActor(tierLine);
    }

    /** Renders from a snapshot + the engine-derived tier (M6 boundary). */
    void update(GameState snapshot, int townTier) {
        StringBuilder sb = new StringBuilder("stock ");
        for (var entry : snapshot.stockpile().entrySet()) {
            sb.append(' ').append(entry.getKey()).append('=')
                    .append(Fixed.unitsFloor(entry.getValue()));
        }
        stockLine.setText(sb);
        tierLine.setText("tier  " + townTier);
    }

    @Override
    public void render(float delta) {
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
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
        stage.dispose();
    }

    /** Test accessor (rules/03 cat. 5: UI state is asserted, not guessed). */
    Stage stageForTest() {
        return stage;
    }
}
