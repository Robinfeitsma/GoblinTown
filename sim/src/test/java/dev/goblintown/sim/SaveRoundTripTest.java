package dev.goblintown.sim;

import dev.goblintown.sim.command.Command;
import dev.goblintown.sim.event.SimEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Save round-trip tests (rules/03 category 3): serialize → deserialize →
 * re-serialize must be byte-identical; the save is version-stamped; corrupt
 * and unknown-version saves fail loudly (H4). Byte-identity doubles as the
 * determinism hash input (rules/03: categories 1+3 intersect by design).
 */
class SaveRoundTripTest {

    @Test
    void serializeDeserializeReserializeIsByteIdentical() {
        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("gatherer", 2));
        engine.execute(new Command.AssignWorkers("miner", 1));
        engine.tick(2_500); // past cap (stone) and at least one level-up

        GameState saved = engine.snapshot();
        String first = SaveCodec.toCanonicalJson(saved);
        SaveCodec.SaveEnvelope reloaded = SaveCodec.fromCanonicalJson(first);
        String second = SaveCodec.toCanonicalJson(reloaded.state());

        assertThat(second).as("the save path must round-trip losslessly (H4)").isEqualTo(first);

        // and the reloaded state must BE the same world, not just look like it
        assertThat(SaveCodec.stateHash(reloaded.state()))
                .isEqualTo(SaveCodec.stateHash(saved));
        assertThat(reloaded.state().tick()).isEqualTo(saved.tick());
        assertThat(reloaded.state().events()).hasSameSizeAs(saved.events());
    }

    @Test
    void hashIsStableAcrossFreshEngineInstances() {
        // The hash is content-derived only — never identity, iteration noise,
        // or JVM-version-dependent serialization (H1: hashable on Windows and
        // Linux dev machines alike, H3).
        SimEngine a = TestSupport.newGame(TestSupport.SEED);
        SimEngine b = TestSupport.newGame(TestSupport.SEED);
        a.tick(1_234);
        b.tick(1_234);
        assertThat(SaveCodec.stateHash(a.snapshot()))
                .isEqualTo(SaveCodec.stateHash(b.snapshot()));
    }

    @Test
    void hashChangesWhenTheWorldChanges() {
        // Guard against a trivially-constant hash (would make the determinism
        // tests vacuous).
        SimEngine a = TestSupport.newGame(TestSupport.SEED);
        SimEngine b = TestSupport.newGame(TestSupport.SEED);
        b.execute(new Command.AssignWorkers("gatherer", 1));
        a.tick(10_000);
        b.tick(10_000);
        assertThat(SaveCodec.stateHash(a.snapshot()))
                .isNotEqualTo(SaveCodec.stateHash(b.snapshot()));
    }

    @Test
    void savesAreVersionStamped() {
        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.tick(10);
        SaveCodec.SaveEnvelope envelope = SaveCodec.fromCanonicalJson(
                SaveCodec.toCanonicalJson(engine.snapshot()));
        assertThat(envelope.version()).isEqualTo(SaveCodec.SAVE_VERSION);
        assertThat(SaveCodec.SAVE_VERSION).isEqualTo(2); // v2 = town layer (docs/09)
    }

    @Test
    void v1SavesMigrateToTheTownLayer() {
        // A real v1 (Phase-1) save: no buildings, no builderAssignments, no
        // nextBuildingSerial — the migration must add an EMPTY town layer
        // (docs/09: v1 states are states with no buildings), and the loaded
        // world must then behave exactly like it always did (H4: migrations
        // are explicit, never destructive).
        SimEngine reference = TestSupport.newGame(TestSupport.SEED);
        reference.execute(new Command.AssignWorkers("gatherer", 2));
        reference.tick(600);

        String v1Json = v1JsonOf(reference.snapshot());
        SaveCodec.SaveEnvelope migrated = SaveCodec.fromCanonicalJson(v1Json);
        assertThat(migrated.version()).isEqualTo(1);
        assertThat(migrated.state().buildings()).isEmpty();
        assertThat(migrated.state().builderAssignments()).isEmpty();
        assertThat(migrated.state().nextBuildingSerial()).isEqualTo(1L);

        // the migrated world is the SAME world: stock, xp, events intact
        assertThat(migrated.state().stockpile())
                .usingRecursiveComparison().isEqualTo(reference.snapshot().stockpile());
        assertThat(migrated.state().roleXp())
                .usingRecursiveComparison().isEqualTo(reference.snapshot().roleXp());
        assertThat(migrated.state().events())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(reference.snapshot().events());

        // re-saving migrates it to the current version
        assertThat(SaveCodec.toCanonicalJson(migrated.state()))
                .contains("\"version\":" + SaveCodec.SAVE_VERSION);

        // and the migrated state keeps playing forward deterministically:
        // catch-up from the migrated save == the v1 world having continued
        SimEngine continued = new SimEngine(TestSupport.balance(), migrated.state());
        reference.tick(5_000);
        continued.catchUp(5_000);
        assertThat(migrated.state().tick()).isEqualTo(600L); // snapshot untouched
        assertThat(Fixed.unitsFloor(continued.snapshot().stockpile().get("wood")))
                .isEqualTo(Fixed.unitsFloor(reference.snapshot().stockpile().get("wood")));
    }

    /** Serializes a v2 snapshot, then strips the town-layer fields — the
     *  exact inverse of the v2 code change (state shape minus town layer). */
    private static String v1JsonOf(GameState state) {
        // NOTE: the canonical mapper sorts keys alphabetically, so the record
        // field order in the JSON is state-first, version-last.
        String v2 = SaveCodec.toCanonicalJson(state)
                .replace("\"version\":" + SaveCodec.SAVE_VERSION, "\"version\":1");
        // drop the added record components (alphabetical field order: they
        // are builderAssignments, buildings, nextBuildingSerial)
        v2 = v2.replaceAll(",\"builderAssignments\":\\{\\}", "")
                .replaceAll(",\"buildings\":\\[\\]", "")
                .replaceAll(",\"nextBuildingSerial\":\\d+", "");
        if (v2.contains("buildings") || v2.contains("nextBuildingSerial")
                || v2.contains("builderAssignments")) {
            throw new IllegalStateException("fixture was not v1-shaped: " + v2);
        }
        return v2;
    }

    @Test
    void unknownFieldsAreRejectedNotIgnored() {
        // A save written by a NEWER build (unknown fields on a known version)
        // must fail at load, not silently drop data (H4: migrations explicit).
        String future = """
                {"version":1,"state":{"tick":5,"stockpile":{},"assignments":{},
                 "roleXp":{},"roleLevel":{},"rngState":0,"events":[],"brandNewThing":true}}
                """;
        assertThatThrownBy(() -> SaveCodec.fromCanonicalJson(future))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newerSaveVersionsAreRejected() {
        String future = """
                {"version":999,"state":{"tick":5,"stockpile":{},"assignments":{},
                 "roleXp":{},"roleLevel":{},"rngState":0,"events":[]}}
                """;
        assertThatThrownBy(() -> SaveCodec.fromCanonicalJson(future))
                .as("newer saves fail loudly; older ones migrate (H4)")
                .hasMessageContaining("newer than this build");
    }

    @Test
    void eventsSurviveTheRoundTrip() {
        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("gatherer", 25));
        engine.tick(2_000); // guaranteed: wood capped, levels gained
        assertThat(engine.snapshot().events()).isNotEmpty();

        String json = SaveCodec.toCanonicalJson(engine.snapshot());
        GameState reloaded = SaveCodec.fromCanonicalJson(json).state();
        assertThat(reloaded.events())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(engine.snapshot().events());
    }

    @Test
    void gzipSaveFormatRoundTripsLosslessly() {
        // H4: saves are written gzipped. The gzip bytes ARE the disk format —
        // must decompress to the exact canonical JSON (rules/03 cat. 3).
        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("miner", 2));
        engine.tick(7_777);

        byte[] gzipped = SaveCodec.toGzipBytes(engine.snapshot());
        byte[] raw = SaveCodec.toCanonicalJson(engine.snapshot())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(gzipped.length)
                .as("gzip must actually compress the canonical JSON")
                .isLessThan(raw.length);

        GameState reloaded = SaveCodec.fromGzipBytes(gzipped).state();
        assertThat(SaveCodec.stateHash(reloaded))
                .isEqualTo(SaveCodec.stateHash(engine.snapshot()));
    }

    @Test
    void gzipRoundTripIsDeterministic() {
        byte[] a = SaveCodec.toGzipBytes(advancedEngine().snapshot());
        byte[] b = SaveCodec.toGzipBytes(advancedEngine().snapshot());
        // Two independent runs → identical save bytes (H1: reproducible
        // anywhere; the gzip layer adds no randomness).
        assertThat(b).isEqualTo(a);
    }

    private static SimEngine advancedEngine() {
        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("miner", 2));
        engine.tick(500);
        return engine;
    }
}
