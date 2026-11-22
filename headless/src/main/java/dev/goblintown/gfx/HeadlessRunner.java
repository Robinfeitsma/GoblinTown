package dev.goblintown.gfx;

import dev.goblintown.sim.Balance;
import dev.goblintown.sim.GameState;
import dev.goblintown.sim.SaveCodec;
import dev.goblintown.sim.SimEngine;
import dev.goblintown.sim.command.Command;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI for the headless simulator (H7): the tool that reproduces any reported
 * state from its seed + command list (rules/03 §10). Talks to {@code :sim}
 * directly, never through core (DR-0001).
 *
 * <pre>
 * Usage:
 *   --balance <file>        balance table (default: data/economy.json)
 *   --seed <long>           rng seed for a fresh game (default 0)
 *   --load <file>           start from a gzipped save instead of a fresh game
 *   --assign <role=count>   assign workers at start (repeatable, before ticks)
 *   --ticks <n>             advance n ticks one-by-one
 *   --catch-up <n>          advance n ticks in closed form (H2)
 *   --save <file>           write the resulting state as a gzipped save
 *   --timeline              print the full event timeline
 *
 * Prints the state hash last — two runs are reproducible iff their hashes
 * match (H1). Exit code 0 on success.
 * </pre>
 */
final class HeadlessRunner {

    int run(String[] args, PrintStream out) {
        try {
            Path balancePath = Path.of("data/economy.json");
            Path loadPath = null;
            Path savePath = null;
            long seed = 0;
            int ticks = 0;
            int catchUpTicks = 0;
            boolean timeline = false;
            List<String> assignments = new ArrayList<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--balance" -> balancePath = Path.of(next(args, ++i, arg));
                    case "--seed" -> seed = Long.parseLong(next(args, ++i, arg));
                    case "--load" -> loadPath = Path.of(next(args, ++i, arg));
                    case "--assign" -> assignments.add(next(args, ++i, arg));
                    case "--ticks" -> ticks = Integer.parseInt(next(args, ++i, arg));
                    case "--catch-up" -> catchUpTicks = Integer.parseInt(next(args, ++i, arg));
                    case "--save" -> savePath = Path.of(next(args, ++i, arg));
                    case "--timeline" -> timeline = true;
                    default -> throw new IllegalArgumentException("unknown option: " + arg);
                }
            }

            if (loadPath != null && (seed != 0 || !assignments.isEmpty())) {
                throw new IllegalArgumentException("--load is a complete starting state; "
                        + "--seed/--assign would make it ambiguous");
            }

            Balance balance = Balance.fromJson(Files.newInputStream(balancePath));
            SimEngine engine;
            if (loadPath != null) {
                engine = new SimEngine(balance,
                        SaveCodec.fromGzipBytes(Files.readAllBytes(loadPath)).state());
                out.println("loaded " + loadPath);
            } else {
                engine = SimEngine.newGame(balance, seed);
            }
            long startTick = engine.tick();
            for (String assignment : assignments) {
                String[] parts = assignment.split("=", 2);
                if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    throw new IllegalArgumentException("--assign expects role=count, got: " + assignment);
                }
                engine.execute(new Command.AssignWorkers(parts[0], Integer.parseInt(parts[1])));
            }

            if (ticks > 0) {
                long t0 = System.nanoTime();
                engine.tick(ticks);
                out.printf("ticks    %d (per-tick, %.1f ms)%n", ticks, (System.nanoTime() - t0) / 1e6);
            }
            if (catchUpTicks > 0) {
                long t0 = System.nanoTime();
                engine.catchUp(catchUpTicks);
                double ms = (System.nanoTime() - t0) / 1e6;
                out.printf("catch-up %d ticks (%.1f ms)%n", catchUpTicks, ms);
            }

            GameState end = engine.snapshot();
            if (timeline) {
                for (dev.goblintown.sim.event.SimEvent event : end.events()) {
                    out.println("event   " + event);
                }
            }
            if (savePath != null) {
                Files.write(savePath, SaveCodec.toGzipBytes(end));
                out.println("saved   " + savePath + " (" + Files.size(savePath) + " bytes gzipped)");
            }

            StringBuilder stock = new StringBuilder("stock   {");
            boolean first = true;
            for (var entry : end.stockpile().entrySet()) {
                if (!first) {
                    stock.append(", ");
                }
                first = false;
                stock.append(entry.getKey()).append("=").append(FixedUnits.of(entry.getValue()));
            }
            out.println(stock.append("}"));
            out.println("ticks   " + startTick + " -> " + end.tick());
            out.println("hash    " + SaveCodec.stateHash(end));
            return 0;
        } catch (IOException e) {
            out.println("ERROR   " + e);
            return 2;
        } catch (RuntimeException e) {
            out.println("ERROR   " + e);
            return 1;
        }
    }

    private static String next(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("missing value for " + option);
        }
        return args[index];
    }

    /** Q32.32 → human units, exact up to 9 fraction digits. */
    private record FixedUnits(long raw) {
        static FixedUnits of(long raw) {
            return new FixedUnits(raw);
        }

        @Override
        public String toString() {
            long whole = raw >> 32;
            long frac = raw & 0xFFFF_FFFFL;
            if (frac == 0) {
                return whole + "";
            }
            String fracStr = String.format("%09d", frac).replaceAll("0+$", "");
            return whole + "." + fracStr;
        }
    }
}
