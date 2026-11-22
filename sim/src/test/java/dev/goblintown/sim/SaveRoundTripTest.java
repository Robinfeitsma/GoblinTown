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
        assertThat(SaveCodec.SAVE_VERSION).isEqualTo(1); // v1 = Phase-1 economy
    }

    @Test
    void unknownFieldsAreRejectedNotIgnored() {
        // A save written by a NEWER build (unknown fields) must fail at load,
        // not silently drop data (H4: migrations are explicit).
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
}
