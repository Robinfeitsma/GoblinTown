package dev.goblintown.gfx;

/**
 * CLI launcher for the headless simulator (H7): run N ticks, offline
 * catch-up calculations, save round-trips and report dumps from the
 * command line. No rendering. Lives OUTSIDE core on purpose — it talks
 * to :sim directly (DR-0001 consequences).
 *
 * <pre>
 * ./gradlew :headless:run --args="--seed 20260211 --assign gatherer=4 --ticks 6000 --timeline"
 * ./gradlew :headless:run --args="--seed 20260211 --assign gatherer=4 --catch-up 1728000"
 * ./gradlew :headless:run --args="--load save.gz --ticks 100 --save out.gz"
 * </pre>
 */
public class HeadlessLauncher {

    public static void main(String[] args) {
        System.exit(new HeadlessRunner().run(args, System.out));
    }
}
