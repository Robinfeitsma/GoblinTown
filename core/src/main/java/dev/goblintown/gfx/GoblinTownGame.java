package dev.goblintown.gfx;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;

/**
 * Glue only. Game truth lives in the sim ring (architecture rule M6:
 * the engine is a slave, not a landlord). Balance numbers, rules and
 * simulation state must NOT appear in this module.
 *
 * <p>Screen graph (docs/10): the founding view is the entry screen; a
 * dev-launch flag can start directly on the town-HUD skeleton (menus and
 * in-game routing come later — dev-routed switches this phase).
 */
public class GoblinTownGame extends Game {

    private final String startScreen;

    public GoblinTownGame() {
        this("founding");
    }

    /** @param startScreen "founding" | "hud" (dev routing, docs/10). */
    public GoblinTownGame(String startScreen) {
        this.startScreen = startScreen;
    }

    @Override
    public void create() {
        setScreen(screenNamed(startScreen));
    }

    private Screen screenNamed(String name) {
        return switch (name) {
            case "hud" -> new TownHudView();
            default -> new FoundingView();
        };
    }
}
