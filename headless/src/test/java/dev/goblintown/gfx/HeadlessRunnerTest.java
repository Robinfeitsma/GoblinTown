package dev.goblintown.gfx;

import dev.goblintown.sim.Balance;
import dev.goblintown.sim.Buildings;
import dev.goblintown.sim.SaveCodec;
import dev.goblintown.sim.SimEngine;
import dev.goblintown.sim.command.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI-level tests for the reproduction tool (rules/03 §10: "headless runs —
 * used to reproduce any reported state from its seed"). Verifies the CLI
 * produces the SAME hash the sim engine does directly — including the
 * buildings flow (build → staff → complete → staff roles) — and that
 * save→load→extend round-trips through the tool.
 */
class HeadlessRunnerTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final HeadlessRunner runner = new HeadlessRunner();

    private Path balanceFile() throws URISyntaxException {
        return Path.of(Balance.class.getResource("/economy.json").toURI());
    }

    private Path buildingsFile() throws URISyntaxException {
        return Path.of(Buildings.class.getResource("/buildings.json").toURI());
    }

    private String[] withTables(String... args) throws Exception {
        String[] all = new String[args.length + 4];
        all[0] = "--balance";
        all[1] = balanceFile().toString();
        all[2] = "--buildings";
        all[3] = buildingsFile().toString();
        System.arraycopy(args, 0, all, 4, args.length);
        return all;
    }

    private String run(String... args) {
        buffer.reset();
        int exit = runner.run(args, new PrintStream(buffer));
        assertThat(exit).as("CLI run failed:\n%s", buffer).isEqualTo(0);
        return buffer.toString();
    }

    private String runFailing(String... args) {
        buffer.reset();
        int exit = runner.run(args, new PrintStream(buffer));
        assertThat(exit).as("CLI run should have failed:\n%s", buffer).isNotEqualTo(0);
        return buffer.toString();
    }

    private String hashOf(String output) {
        return output.lines().filter(l -> l.startsWith("hash    "))
                .map(l -> l.substring("hash    ".length())).findFirst().orElseThrow();
    }

    private static InputStream table(String name) {
        return Balance.class.getResourceAsStream(name);
    }

    @Test
    void cliMatchesTheEngineBitForBit() throws Exception {
        String cliOutput = run(withTables(
                "--seed", "20260211",
                "--build", "wood-camp",
                "--build-assign", "wood-camp-000001=8",
                "--ticks", "563",
                "--assign", "gatherer=4",
                "--ticks", "6000"));
        assertThat(cliOutput).contains("tier    0");

        // the same world built directly through the engine API
        Balance balance = Balance.fromJson(table("/economy.json"));
        Buildings buildings = Buildings.fromJson(table("/buildings.json"), balance);
        SimEngine engine = SimEngine.newGame(balance, buildings, 20260211);
        engine.execute(new Command.Build("wood-camp"));
        engine.execute(new Command.AssignBuilders("wood-camp-000001", 8));
        engine.tick(563);
        engine.execute(new Command.AssignWorkers("gatherer", 4));
        engine.tick(6000);

        assertThat(hashOf(cliOutput)).isEqualTo(SaveCodec.stateHash(engine.snapshot()));
    }

    @Test
    void cliCatchUpEqualsCliTickCountingIncludingConstruction() throws Exception {
        // The founding segment: one side ticks it live, the other resolves it
        // in closed form — with a construction site completing mid-window and
        // role assignments after the completion (cap re-segmentation
        // exercised through the CLI, not just the engine).
        String live = run(withTables(
                "--seed", "20260211",
                "--build", "wood-camp",
                "--build-assign", "wood-camp-000001=8",
                "--ticks", "563",
                "--assign", "gatherer=4",
                "--ticks", "6000"));
        String blended = run(withTables(
                "--seed", "20260211",
                "--build", "wood-camp",
                "--build-assign", "wood-camp-000001=8",
                "--catch-up", "563",
                "--assign", "gatherer=4",
                "--catch-up", "6000"));

        assertThat(hashOf(blended)).as("CLI catch-up vs CLI tick-counting")
                .isEqualTo(hashOf(live));
    }

    @Test
    void cliSaveThenLoadRoundTrips(@TempDir Path tmp) throws Exception {
        Path savePath = tmp.resolve("save.gz");
        String first = run(withTables(
                "--seed", "99",
                "--build", "wood-camp",
                "--build-assign", "wood-camp-000001=8",
                "--ticks", "563",
                "--assign", "gatherer=4",
                "--ticks", "5000",
                "--save", savePath.toString()));
        assertThat(Files.exists(savePath)).isTrue();

        // load the save and extend — must match an engine that never stopped
        String second = run(withTables("--load", savePath.toString(), "--ticks", "5000"));

        Balance balance = Balance.fromJson(table("/economy.json"));
        Buildings buildings = Buildings.fromJson(table("/buildings.json"), balance);
        SimEngine engine = SimEngine.newGame(balance, buildings, 99);
        engine.execute(new Command.Build("wood-camp"));
        engine.execute(new Command.AssignBuilders("wood-camp-000001", 8));
        engine.tick(563);
        engine.execute(new Command.AssignWorkers("gatherer", 4));
        engine.tick(10_000);
        assertThat(hashOf(second)).isEqualTo(SaveCodec.stateHash(engine.snapshot()));
        assertThat(first).contains("saved   ");
    }

    @Test
    void cliEnforcesUnlockAndSlotsAndKnownTypes() throws Exception {
        // requires gate: the quarry needs a completed wood camp
        runFailing(withTables("--seed", "5", "--build", "stone-quarry"));
        // unknown building type
        runFailing(withTables("--seed", "5", "--build", "opc"));
        // slots gate: camp exists but is not complete → capacity 0
        runFailing(withTables("--seed", "5", "--build", "wood-camp",
                "--assign", "gatherer=1"));
        // unknown role
        runFailing(withTables("--seed", "5", "--assign", "wizard=1"));
    }

    @Test
    void cliCancelRefundsAndRejectsCompletedCancellations() throws Exception {
        String output = run(withTables(
                "--seed", "5",
                "--build", "food-larder",
                "--ticks", "100",
                "--cancel", "food-larder-000001",
                "--timeline"));
        assertThat(output).contains("SiteCancelled");
        runFailing(withTables("--seed", "5",
                "--build", "wood-camp",
                "--build-assign", "wood-camp-000001=8",
                "--ticks", "563",
                "--cancel", "wood-camp-000001"));
    }

    @Test
    void cliRejectsBadArgsAndUnknownFiles(@TempDir Path tmp) {
        // exit 1 = bad arguments / unparseable data; exit 2 = filesystem I/O
        assertThat(runner.run(new String[]{"--bogus"}, new PrintStream(buffer))).isEqualTo(1);
        assertThat(runner.run(new String[]{"--balance", "/nope.json"},
                new PrintStream(buffer))).isEqualTo(2);
        assertThat(runner.run(new String[]{"--balance", tmp.toString(), "--ticks", "1"},
                new PrintStream(buffer))).isEqualTo(1); // unreadable table = parse failure
        assertThat(runner.run(new String[]{"--load", "/nope.gz"},
                new PrintStream(buffer))).isEqualTo(2);
    }
}
