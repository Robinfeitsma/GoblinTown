package dev.goblintown.gfx;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Desktop launcher (Windows/Linux, H3). Launch-related code only. */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("GoblinTown");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        new Lwjgl3Application(new GoblinTownGame(), config);
    }
}
