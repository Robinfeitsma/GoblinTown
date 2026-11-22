package dev.goblintown.gfx;

import com.badlogic.gdx.Game;

/**
 * Glue only. Game truth lives in the sim ring (architecture rule M6:
 * the engine is a slave, not a landlord). Balance numbers, rules and
 * simulation state must NOT appear in this module.
 */
public class GoblinTownGame extends Game {

    @Override
    public void create() {
        // TODO: push the first screen (founding view) — first render task.
    }
}
