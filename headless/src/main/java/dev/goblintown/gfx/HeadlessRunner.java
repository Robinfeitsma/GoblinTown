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
 *   --buildings <file>      building table (default: data/buildings.json)
 *   --seed <long>           rng seed for a fresh game (default 0)
 *   --load <file>           start from a gzipped save instead of a fresh game
 *   --build <type>          start a construction site (repeatable, ordered)
 *   --build-assign <id=count>   put goblins on a site (repeatable, ordered)
 *   --build-unassign <id=count> pull goblins off a site (repeatable, ordered)
 *   --cancel <buildingId>   cancel an in-progress site, full refund
 *   --assign <role=count>   assign workers (repeatable; runs in arg order,
 *                           interleaved with --ticks/--catch-up!)
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
            Path buildingsPath = Path.of("data/buildings.json");
            Path loadPath = null;
            Path savePath = null;
            long seed = 0;
            boolean timeline = false;
            // steps run in ARGUMENT ORDER: commands and tick/catch-up batches
            // interleave exactly the way the user wrote them (the foundation
            // flow is sequential: build → staff → wait → staff roles → wait)
            List<Object> steps = new ArrayList<>(); // Command | TickBatch

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--balance" -> balancePath = Path.of(next(args, ++i, arg));
                    case "--buildings" -> buildingsPath = Path.of(next(args, ++i, arg));
                    case "--seed" -> seed = Long.parseLong(next(args, ++i, arg));
                    case "--load" -> loadPath = Path.of(next(args, ++i, arg));
                    case "--build" -> steps.add(new Command.Build(next(args, ++i, arg)));
                    case "--cancel" -> steps.add(new Command.CancelSite(next(args, ++i, arg)));
                    case "--build-assign" -> {
                        Arg2 parts = idParts(next(args, ++i, arg));
                        steps.add(new Command.AssignBuilders(parts.id(), parts.count()));
                    }
                    case "--build-unassign" -> {
                        Arg2 parts = idParts(next(args, ++i, arg));
                        steps.add(new Command.UnassignBuilders(parts.id(), parts.count()));
                    }
                    case "--assign" -> {
                        Arg2 parts = roleCount(next(args, ++i, arg), "--assign");
                        steps.add(new Command.AssignWorkers(parts.id(), parts.count()));
                    }
                    case "--unassign" -> {
                        Arg2 parts = roleCount(next(args, ++i, arg), "--unassign");
                        steps.add(new Command.UnassignWorkers(parts.id(), parts.count()));
                    }
                    case "--ticks" -> steps.add(new TickBatch(Integer.parseInt(next(args, ++i, arg)), false));
                    case "--catch-up" -> steps.add(new TickBatch(Integer.parseInt(next(args, ++i, arg)), true));
                    case "--save" -> savePath = Path.of(next(args, ++i, arg));
                    case "--timeline" -> timeline = true;
                    default -> throw new IllegalArgumentException("unknown option: " + arg);
                }
            }

            boolean loadHasCommands = steps.stream().anyMatch(s -> s instanceof Command);
            if (loadPath != null && (seed != 0 || loadHasCommands)) {
                throw new IllegalArgumentException("--load is a complete starting state; "
                        + "--seed/--assign/--build/--cancel would make it ambiguous "
                        + "(--ticks/--catch-up may follow a load)");
            }

            Balance balance = Balance.fromJson(Files.newInputStream(balancePath));
            dev.goblintown.sim.Buildings buildings =
                    dev.goblintown.sim.Buildings.fromJson(
                            Files.newInputStream(buildingsPath), balance);
            SimEngine engine;
            if (loadPath != null) {
                engine = new SimEngine(balance, buildings,
                        SaveCodec.fromGzipBytes(Files.readAllBytes(loadPath)).state());
                out.println("loaded " + loadPath);
            } else {
                engine = SimEngine.newGame(balance, buildings, seed);
            }
            long startTick = engine.tick();
            for (Object step : steps) {
                if (step instanceof Command command) {
                    engine.execute(command);
                } else {
                    TickBatch batch = (TickBatch) step;
                    if (batch.ticks() <= 0) {
                        continue;
                    }
                    long t0 = System.nanoTime();
                    if (batch.catchUp()) {
                        engine.catchUp(batch.ticks());
                        out.printf("catch-up %d ticks (%.1f ms)%n",
                                batch.ticks(), (System.nanoTime() - t0) / 1e6);
                    } else {
                        engine.tick(batch.ticks());
                        out.printf("ticks    %d (per-tick, %.1f ms)%n",
                                batch.ticks(), (System.nanoTime() - t0) / 1e6);
                    }
                }
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
            out.println("tier    " + engine.townTier());
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

    /** One {@code id=count} / {@code role=count} argument, parsed. */
    private record Arg2(String id, int count) {
    }

    /** Splits {@code buildingId=count} for builder options. */
    private static Arg2 idParts(String value) {
        String[] parts = value.split("=", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("expects buildingId=count, got: " + value);
        }
        try {
            int count = Integer.parseInt(parts[1]);
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive: " + value);
            }
            return new Arg2(parts[0], count);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expects buildingId=count, got: " + value);
        }
    }

    /** Splits {@code role=count} for worker options. */
    private static Arg2 roleCount(String value, String option) {
        String[] parts = value.split("=", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(option + " expects role=count, got: " + value);
        }
        try {
            int count = Integer.parseInt(parts[1]);
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive: " + value);
            }
            return new Arg2(parts[0], count);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " expects role=count, got: " + value);
        }
    }

    /** An ordered CLI step: advance some ticks, per-tick or closed-form. */
    private record TickBatch(int ticks, boolean catchUp) {
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
