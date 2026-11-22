package dev.goblintown.gfx;

import dev.goblintown.sim.Balance;
import dev.goblintown.sim.SaveCodec;
import dev.goblintown.sim.SimEngine;
import dev.goblintown.sim.command.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI-level tests for the reproduction tool (rules/03 §10: "headless runs —
 * used to reproduce any reported state from its seed"). Verifies the CLI
 * produces the SAME hash the sim engine does directly, and save→load→extend
 * round-trips through the tool.
 */
class HeadlessRunnerTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final HeadlessRunner runner = new HeadlessRunner();

    private Path balanceFile() throws URISyntaxException {
        return Path.of(Balance.class.getResource("/economy.json").toURI());
    }

    private String run(String... args) {
        buffer.reset();
        int exit = runner.run(args, new PrintStream(buffer));
        assertThat(exit).as("CLI run failed:\n%s", buffer).isEqualTo(0);
        return buffer.toString();
    }

    private String hashOf(String output) {
        return output.lines().filter(l -> l.startsWith("hash    "))
                .map(l -> l.substring("hash    ".length())).findFirst().orElseThrow();
    }

    @Test
    void cliMatchesTheEngineBitForBit() throws Exception {
        String cliOutput = run("--balance", balanceFile().toString(),
                "--seed", "20260211", "--assign", "gatherer=4", "--ticks", "6000");

        // the same world built directly through the engine API
        SimEngine engine = SimEngine.newGame(Balance.fromJson(Balance.class
                .getResource("/economy.json").openStream()), 20260211);
        engine.execute(new Command.AssignWorkers("gatherer", 4));
        engine.tick(6000);

        assertThat(hashOf(cliOutput)).isEqualTo(SaveCodec.stateHash(engine.snapshot()));
    }

    @Test
    void cliCatchUpEqualsCliTickCounting() throws Exception {
        // 8h horizon (H2): one side ticks it live, other side ticks 10k then
        // catches up the rest in closed form — same hash, from the CLI.
        int eightHours = Balance.TICKS_PER_SECOND * 60 * 60 * 8;
        String live = run("--balance", balanceFile().toString(),
                "--seed", "7", "--assign", "gatherer=3", "--assign", "miner=2",
                "--ticks", String.valueOf(eightHours));
        String blended = run("--balance", balanceFile().toString(),
                "--seed", "7", "--assign", "gatherer=3", "--assign", "miner=2",
                "--ticks", "10000", "--catch-up", String.valueOf(eightHours - 10000));

        assertThat(hashOf(blended)).as("CLI catch-up vs CLI tick-counting")
                .isEqualTo(hashOf(live));
    }

    @Test
    void cliSaveThenLoadRoundTrips(@TempDir Path tmp) throws Exception {
        Path savePath = tmp.resolve("save.gz");
        String first = run("--balance", balanceFile().toString(),
                "--seed", "99", "--assign", "gatherer=2", "--ticks", "5000",
                "--save", savePath.toString());
        assertThat(Files.exists(savePath)).isTrue();

        // load the save and extend — must match an engine that never stopped
        String second = run("--balance", balanceFile().toString(),
                "--load", savePath.toString(), "--ticks", "5000");

        SimEngine engine = SimEngine.newGame(Balance.fromJson(Balance.class
                .getResource("/economy.json").openStream()), 99);
        engine.execute(new Command.AssignWorkers("gatherer", 2));
        engine.tick(10_000);
        assertThat(hashOf(second)).isEqualTo(SaveCodec.stateHash(engine.snapshot()));
        assertThat(first).contains("saved   ");
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
