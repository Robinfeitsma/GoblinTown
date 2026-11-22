package dev.goblintown.gfx;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import dev.goblintown.sim.Balance;
import dev.goblintown.sim.Buildings;
import dev.goblintown.sim.Fixed;
import dev.goblintown.sim.GameState;
import dev.goblintown.sim.SimEngine;
import dev.goblintown.sim.command.Command;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Town-HUD skeleton (docs/10): renders from a REAL SimEngine snapshot —
 * reads a live-simulated state, asserts the panel text (rules/03 cat. 5: UI
 * state mirrors the sim boundary; no expectations copied from fixtures).
 */
class TownHudViewTest {

    private static HeadlessApplication app;

    @Test
    void hudMirrorsTheEngineSnapshot() {
        app = new HeadlessApplication(new com.badlogic.gdx.ApplicationListener() {
            @Override public void create() { }
            @Override public void render() { }
            @Override public void resize(int width, int height) { }
            @Override public void pause() { }
            @Override public void resume() { }
            @Override public void dispose() { }
        }, new com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration());
        HeadlessGl.install();

        Balance.Raw raw = new Balance.Raw();
        raw.resources = List.of(new Balance.ResourceDef("wood", 900L));
        raw.roles = List.of(new Balance.RoleDef(
                "gatherer", "wood", 3L, 2L, 100L, 50L));
        raw.foundingSupplies = Map.of("wood", 100L);
        Balance balance = Balance.of(raw);

        SimEngine engine = SimEngine.newGame(balance, Buildings.none(), 20260211L);
        TownHudView hud = new TownHudView();
        try {
            hud.show();
            engine.execute(new Command.AssignWorkers("gatherer", 2));
            engine.tick(600);

            // expected stock derives from the documented conversion rules
            // (DR-0003: per-tick truncation once at load; rules/03: expected
            // values come from data tables, never copied fixtures):
            // founding 100 + 2 workers × amountPerTickFixed × 600 ticks.
            Balance.RoleDef gatherer = balance.role("gatherer");
            long expectedWood = 100L + Fixed.unitsFloor(
                    2L * gatherer.amountPerTickFixed() * 600);

            hud.update(engine.snapshot(), engine.townTier());
            Label stock = stageLabelOf(hud, 0);
            Label tier = stageLabelOf(hud, 1);
            assertThat(stock.getText().toString())
                    .contains("wood=" + expectedWood);
            assertThat(tier.getText().toString()).contains("tier");

            hud.render(1f / 60f); // must not throw under the shim
            hud.resize(800, 600);
        } finally {
            hud.dispose();
        }
    }

    private static Label stageLabelOf(TownHudView hud, int index) {
        return (Label) hud.stageForTest().getActors().get(index);
    }

    @AfterAll
    static void shutDown() {
        if (app != null) {
            app.exit();
        }
        HeadlessGl.restore();
    }
}
