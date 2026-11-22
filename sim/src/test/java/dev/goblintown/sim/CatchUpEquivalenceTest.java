package dev.goblintown.sim;

import dev.goblintown.sim.command.Command;
import dev.goblintown.sim.event.SimEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hardens the closed-form catch-up (H2) beyond the golden-seed smoke test:
 * multiple segments with commands interleaved, no-op catch-ups, resume
 * equivalence in both directions, and the cross-role event-timeline order.
 */
class CatchUpEquivalenceTest {

    private static final long SEED = TestSupport.SEED;

    @Test
    void segmentedCatchUpEqualsLivePlayAcrossCommands() {
        // Five segments, commands punch in and out mid-history: each segment
        // resolved in ONE closed-form call on the offline side; live side
        // ticks. Final states must be byte-identical (rules/03 cat. 2).
        int[] segments = {97, 1201, 60_000, 31, 500_000};
        TestSupport.ScenarioStep[] commands = {
                new TestSupport.ScenarioStep(new Command.AssignWorkers("gatherer", 3), 0),
                new TestSupport.ScenarioStep(new Command.UnassignWorkers("gatherer", 2), 0),
                new TestSupport.ScenarioStep(new Command.AssignWorkers("miner", 7), 0),
                new TestSupport.ScenarioStep(new Command.AssignWorkers("gatherer", 50), 0),
                new TestSupport.ScenarioStep(new Command.UnassignWorkers("miner", 20), 0),
        };

        SimEngine live = TestSupport.newGame(SEED);
        SimEngine offline = TestSupport.newGame(SEED);
        for (int i = 0; i < segments.length; i++) {
            live.execute(commands[i].command());
            offline.execute(commands[i].command());
            live.tick(segments[i]);
            offline.catchUp(segments[i]);
            // the worlds must agree after EVERY segment, not just at the end
            assertThat(SaveCodec.stateHash(offline.snapshot()))
                    .as("segment %d divergence", i)
                    .isEqualTo(SaveCodec.stateHash(live.snapshot()));
        }
    }

    @Test
    void catchUpIsIdempotentAndSafeAtZero() {
        SimEngine engine = TestSupport.newGame(SEED);
        engine.execute(new Command.AssignWorkers("gatherer", 2));
        String baseline = SaveCodec.stateHash(engine.snapshot());

        engine.catchUp(0);
        assertThat(SaveCodec.stateHash(engine.snapshot())).isEqualTo(baseline);
        assertThatThrownByNegativeTicks(engine);

        engine.catchUp(1_000);
        engine.catchUp(0);
        // catching up 0 ticks after any point must not move the world
        String after = SaveCodec.stateHash(engine.snapshot());
        engine.catchUp(0);
        assertThat(SaveCodec.stateHash(engine.snapshot())).isEqualTo(after);
    }

    private void assertThatThrownByNegativeTicks(SimEngine engine) {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> engine.catchUp(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void catchUpFromRestoredSaveMatchesTheOriginalEngine() {
        // The offline scenario that actually happens: save → (time passes in
        // the real world) → load → catch-up. The loaded save must catch up to
        // the same bytes as the engine that would have kept running.
        SimEngine live = TestSupport.newGame(SEED);
        live.execute(new Command.AssignWorkers("gatherer", 4));
        live.execute(new Command.AssignWorkers("miner", 2));
        live.tick(3_141);

        // save + restore through the real (gzip) disk format
        SimEngine restored = new SimEngine(TestSupport.balance(),
                SaveCodec.fromGzipBytes(SaveCodec.toGzipBytes(live.snapshot())).state());

        live.tick(120_000);
        restored.catchUp(120_000);

        assertThat(SaveCodec.stateHash(restored.snapshot()))
                .isEqualTo(SaveCodec.stateHash(live.snapshot()));
    }

    @Test
    void eventTimelineOrderIsChronologicalWithRoleTieBreak() {
        // Structure of the rules/03 convention: events ascending by tick, and
        // at equal ticks roles in balance order, level-ups before cap events.
        int stretch = 200_000;
        SimEngine live = TestSupport.newGame(SEED);
        live.execute(new Command.AssignWorkers("gatherer", 6));
        live.execute(new Command.AssignWorkers("miner", 6));
        live.tick(stretch);

        List<SimEvent> events = live.snapshot().events();
        assertThat(events).as("scenario must produce a non-trivial timeline").hasSizeGreaterThan(5);

        for (int i = 1; i < events.size(); i++) {
            long prevTick = events.get(i - 1).tick();
            long curTick = events.get(i).tick();
            assertThat(curTick).as("tick monotonicity at %d", i).isGreaterThanOrEqualTo(prevTick);
            if (curTick == prevTick) {
                // same tick: role order (gatherer before miner), or same role
                // with level-up before stockpile-full
                String prevRole = roleOf(events.get(i - 1));
                String curRole = roleOf(events.get(i));
                if (prevRole != null && curRole != null) {
                    assertThat(score(live, events.get(i)))
                            .as("same-tick order at tick %d: %s then %s", curTick, events.get(i - 1), events.get(i))
                            .isGreaterThanOrEqualTo(score(live, events.get(i - 1)));
                }
            }
        }

        // and the closed-form path must reproduce exactly this timeline
        SimEngine offline = TestSupport.newGame(SEED);
        offline.execute(new Command.AssignWorkers("gatherer", 6));
        offline.execute(new Command.AssignWorkers("miner", 6));
        offline.catchUp(stretch);
        assertThat(offline.snapshot().events())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(events);
    }

    private static String roleOf(SimEvent event) {
        if (event instanceof SimEvent.RoleLevelUp levelUp) {
            return levelUp.roleId();
        }
        if (event instanceof SimEvent.StockpileFull stockpileFull) {
            return stockpileFull.resourceId();
        }
        return null;
    }

    /** (roleLoopPosition, kindRank) — mirrors SimEngine's canonical
     *  tie-break: a StockpileFull belongs to the role producing that
     *  resource, exactly where tickOnce's role loop emits it. */
    private static long score(SimEngine engine, SimEvent event) {
        List<Balance.RoleDef> roles = TestSupport.balance().roles();
        String roleOrResourceId = roleOf(event);
        int roleIndex = -1;
        for (int i = 0; i < roles.size(); i++) {
            boolean match = roles.get(i).id().equals(roleOrResourceId)
                    || roleOrResourceId.equals(roles.get(i).produces());
            if (match) {
                roleIndex = i;
                break;
            }
        }
        int kindRank = event instanceof SimEvent.RoleLevelUp ? 0 : 1;
        return (long) (roleIndex + 1) * 2 + kindRank;
    }

    @Test
    void capFillAndLevelUpCoinidingOnOneTickKeepCanonicalOrder() {
        // Deterministically FORCES the collision the catch-up comparator must
        // survive: roleA fills its stockpile exactly at tick 10, roleB levels
        // up exactly at tick 10. tickOnce order: [Full(r1), LevelUp(b)] —
        // catch-up must reproduce this identical timeline (H1).
        Balance.Raw raw = new Balance.Raw();
        raw.resources = List.of(
                new Balance.ResourceDef("r1", 10),
                new Balance.ResourceDef("r2", 100));
        raw.roles = List.of(
                // 1 unit/tick; xp growth 1/level pins exactly ONE crossing at tick 10
                new Balance.RoleDef("a", "r1", 60, 0, 10, 1),
                new Balance.RoleDef("b", "r2", 0, 60, 10, 1));
        Balance collision = Balance.of(raw);

        SimEngine live = new SimEngine(collision, GameState.initial(collision, 1));
        live.execute(new Command.AssignWorkers("a", 1));
        live.execute(new Command.AssignWorkers("b", 1));
        live.tick(10);
        assertThat(live.snapshot().events()).containsExactly(
                new SimEvent.StockpileFull(10, "r1"),
                new SimEvent.RoleLevelUp(10, "b", 2));

        SimEngine offline = new SimEngine(collision, GameState.initial(collision, 1));
        offline.execute(new Command.AssignWorkers("a", 1));
        offline.execute(new Command.AssignWorkers("b", 1));
        offline.catchUp(10);
        assertThat(offline.snapshot().events())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(live.snapshot().events());
    }
}
