package dev.goblintown.gfx;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Desktop launcher (Windows/Linux, H3). Launch-related code only. */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("GoblinTown");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);

        ApplicationListener app = new GoblinTownGame();
        // acceptance instruments launched from the same binary (DR-0001
        // validation tasks 3–4); the game itself never sees these args
        for (int i = 0; i < args.length - 1; i++) {
            if ("--fixture".equals(args[i]) && "overlay".equals(args[i + 1])) {
                app = new PlanOverlayFixture();
                break;
            }
            if ("--fixture".equals(args[i]) && "horde".equals(args[i + 1])) {
                app = new HordeRenderFixture();
                break;
            }
            if ("--fixture".equals(args[i]) && "hud".equals(args[i + 1])) {
                app = new GoblinTownGame("hud");
                break;
            }
        }
        new Lwjgl3Application(app, config);
    }
}
