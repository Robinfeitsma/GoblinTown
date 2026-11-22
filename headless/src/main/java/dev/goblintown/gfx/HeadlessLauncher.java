package dev.goblintown.gfx;

/**
 * CLI launcher for the headless simulator (H7): run N ticks, offline
 * catch-up calculations, save round-trips and report dumps from the
 * command line. No rendering. Lives OUTSIDE core on purpose — it talks
 * to :sim directly (DR-0001 consequences).
 */
public class HeadlessLauncher {

    public static void main(String[] args) {
        // TODO: argument parsing — first headless task:
        //   --ticks N --seed S         run sim N ticks from seed, print final state hash
        //   --catch-up 8h --seed S     offline catch-up equivalence run (rules/03 cat. 2)
        System.out.println("GoblinTown headless simulator — not yet implemented");
    }
}
