package dev.goblintown.sim;

import dev.goblintown.sim.command.Command;
import dev.goblintown.sim.event.SimEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Buildings &amp; construction semantics (docs/09, approved) at the simulation
 * level — real tables via the test classpath (rules/03), synthetic tables only
 * where an edge case needs numbers the approved data can't hit (cap
 * re-segmentation across a completion boundary, tier derivation).
 *
 * <p>Derived expectations, from the approved numbers:
 * <ul>
 *   <li>wood camp: cost 450 wood, progressCost 4500 — 8 builders at 1
 *       point/tick = 562.5 → completes on tick 563 (ceilDiv).</li>
 *   <li>stone quarry: same math at 4 builders → tick 1125 after its Build.</li>
 *   <li>slots: gatherer 4 (camp), miner 4 (quarry).</li>
 * </ul>
 */
class BuildingSimTest {

    // ---------------------------------------------------------------
    // the founding flow against the REAL approved tables
    // ---------------------------------------------------------------

    @Test
    void buildFlowCompletesExactlyOnTheDerivedTick() {
        SimEngine engine = TestSupport.townGame(TestSupport.SEED);

        List<SimEvent.BuildingFinished> finished = engine.snapshot().events().stream()
                .filter(SimEvent.BuildingFinished.class::isInstance)
                .map(SimEvent.BuildingFinished.class::cast)
                .toList();
        assertThat(finished).hasSize(1);
        assertThat(finished.get(0).tick()).isEqualTo(TestSupport.CAMP_COMPLETION_TICK);
        assertThat(finished.get(0).buildingId()).isEqualTo(TestSupport.CAMP_ID);
        assertThat(finished.get(0).buildingType()).isEqualTo("wood-camp");

        Building camp = engine.snapshot().buildings().get(0);
        assertThat(camp.complete()).isTrue();
        assertThat(camp.progress()).isEqualTo(4500L); // clamped at progressCost
        assertThat(engine.roleCapacity("gatherer")).isEqualTo(4);
        assertThat(engine.townTier()).isZero(); // camp grantsTier 0
    }

    @Test
    void siteCostIsDeductedUpfrontAndRefundedInFullOnCancel() {
        SimEngine engine = TestSupport.freshTown(TestSupport.SEED);
        long founding = TestSupport.balance().foundingSupply("wood"); // 450
        assertThat(Fixed.unitsFloor(engine.snapshot().stockpile().get("wood")))
                .isEqualTo(founding);

        engine.execute(new Command.Build("food-larder")); // 300 wood
        assertThat(Fixed.unitsFloor(engine.snapshot().stockpile().get("wood")))
                .as("cost is deducted at build confirmation, upfront (docs/09)")
                .isEqualTo(150); // 450 founding − 300 cost

        engine.tick(500); // progressCost 3000, nobody building: site incomplete
        assertThat(engine.snapshot().buildings().get(0).progress()).isZero();

        engine.execute(new Command.CancelSite("food-larder-000001"));
        assertThat(Fixed.unitsFloor(engine.snapshot().stockpile().get("wood")))
                .as("full refund (docs/09 OQ-2), progress invested is not billed")
                .isEqualTo(founding);
        assertThat(engine.snapshot().buildings()).isEmpty();

        List<SimEvent.SiteCancelled> cancels = engine.snapshot().events().stream()
                .filter(SimEvent.SiteCancelled.class::isInstance)
                .map(SimEvent.SiteCancelled.class::cast)
                .toList();
        assertThat(cancels).hasSize(1);
        assertThat(cancels.get(0).tick()).isEqualTo(500); // command-time, current tick
        assertThat(cancels.get(0).buildingId()).isEqualTo("food-larder-000001");
    }

    @Test
    void completedSitesCannotBeCancelledOrBuiltOn() {
        SimEngine engine = TestSupport.townGame(TestSupport.SEED);
        assertThatThrownBy(() -> engine.execute(new Command.CancelSite(TestSupport.CAMP_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot cancel a completed building");
        assertThatThrownBy(() ->
                engine.execute(new Command.AssignBuilders(TestSupport.CAMP_ID, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot assign builders to a completed building");
    }

    @Test
    void unknownSiteIdsAreRejected() {
        SimEngine engine = TestSupport.townGame(TestSupport.SEED);
        assertThatThrownBy(() -> engine.execute(new Command.CancelSite("wood-camp-000009")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no such building site");
        assertThatThrownBy(() ->
                engine.execute(new Command.AssignBuilders("wood-camp-000009", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                engine.execute(new Command.UnassignBuilders("wood-camp-000009", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slotsGateAssignments() {
        SimEngine preCamp = TestSupport.freshTown(TestSupport.SEED);
        assertThatThrownBy(() ->
                preCamp.execute(new Command.AssignWorkers("gatherer", 1)))
                .as("no completed building hosting the role — nothing to work at")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds capacity 0");

        SimEngine engine = TestSupport.townGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("gatherer", 4));
        assertThatThrownBy(() -> engine.execute(new Command.AssignWorkers("gatherer", 1)))
                .as("camp has exactly 4 slots (docs/09 approved table)")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds capacity 4");

        engine.execute(new Command.UnassignWorkers("gatherer", 3));
        engine.execute(new Command.AssignWorkers("gatherer", 3)); // back to 4, no leak
    }

    @Test
    void requiresGateBlocksEarlyBuild() {
        SimEngine fresh = TestSupport.freshTown(TestSupport.SEED);
        assertThatThrownBy(() -> fresh.execute(new Command.Build("stone-quarry")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wood-camp");

        // unlock is one gate; affordability is the other: goblin-hall IS
        // startUnlocked but needs 300 stone the founding town never had
        assertThatThrownBy(() -> fresh.execute(new Command.Build("goblin-hall")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs");
    }

    @Test
    void quarryFundsCompletesOnItsDerivedTickAndHostsMiners() {
        // Fund the quarry via the game's own economy: camp complete → 4
        // gatherers → cap 900 in ~26s → afford the 450-wood quarry (the
        // DeterminismSmokeTest scenario runs the same flow; here we pin the
        // derived construction + hosting numbers, not the hashes).
        SimEngine engine = TestSupport.townGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("gatherer", 4));
        engine.tick(12_000); // wood hits its 900 cap long before this
        engine.execute(new Command.Build("stone-quarry")); // id: zero-padded serial
        assertThat(engine.snapshot().buildings().get(1).id())
                .isEqualTo("stone-quarry-000002");

        engine.execute(new Command.AssignBuilders("stone-quarry-000002", 4));
        engine.tick(1125); // floorDiv-free: ceilDiv(4500, 4) = 1125
        List<SimEvent.BuildingFinished> finished = engine.snapshot().events().stream()
                .filter(SimEvent.BuildingFinished.class::isInstance)
                .map(SimEvent.BuildingFinished.class::cast)
                .toList();
        assertThat(finished).hasSize(2);
        assertThat(finished.get(1).tick())
                .as("completes exactly ceilDiv(progressCost, builders) ticks after its Build")
                .isEqualTo(engine.tick());
        assertThat(Fixed.unitsFloor(engine.snapshot().roleXp().get("miner"))).isZero();
        assertThat(engine.roleCapacity("miner")).isEqualTo(4);
    }

    @Test
    void buildersProduceNothingAndSpendNoXp() {
        SimEngine engine = TestSupport.townGame(TestSupport.SEED);
        // 563 ticks of 8 builders: no wood produced beyond the (spent) founding
        // supply, no XP accrued, builders stood down on completion
        assertThat(Fixed.unitsFloor(engine.snapshot().stockpile().get("wood"))).isZero();
        assertThat(engine.snapshot().roleXp().get("gatherer")).isZero();
        assertThat(engine.snapshot().roleLevel().get("gatherer")).isEqualTo(1);
        assertThat(engine.snapshot().builderAssignments()).isEmpty();
    }

    @Test
    void builderCountCanBeReassignedMidConstruction() {
        SimEngine engine = TestSupport.freshTown(TestSupport.SEED);
        engine.execute(new Command.Build("wood-camp"));
        engine.execute(new Command.AssignBuilders(TestSupport.CAMP_ID, 8));
        engine.tick(100); // 800 points
        engine.execute(new Command.UnassignBuilders(TestSupport.CAMP_ID, 8));
        engine.execute(new Command.AssignBuilders(TestSupport.CAMP_ID, 4)); // 4/tick now
        engine.tick(925); // 800 + 925*4 = 4500 → complete on tick 1025
        List<SimEvent.BuildingFinished> finished = engine.snapshot().events().stream()
                .filter(SimEvent.BuildingFinished.class::isInstance)
                .map(SimEvent.BuildingFinished.class::cast)
                .toList();
        assertThat(finished).hasSize(1);
        assertThat(finished.get(0).tick()).isEqualTo(1025);
        // progress stops exactly at cost even though 4500+ overshoot of the
        // last tick is truncated
        assertThat(engine.snapshot().buildings().get(0).progress()).isEqualTo(4500L);
    }

    // ---------------------------------------------------------------
    // synthetic tables: edges the approved data can't hit
    // ---------------------------------------------------------------

    /** Balance: one resource ("food", cap 10), one role producing 1/tick
     *  exactly (unitsPerSecond 60), no XP growth, founding supply 8. */
    private static Balance tinyBalance() {
        Balance.Raw raw = new Balance.Raw();
        raw.resources = List.of(new Balance.ResourceDef("food", 10L));
        raw.roles = List.of(new Balance.RoleDef(
                "forager", "food", 60L, 0L, 100L, 0L));
        raw.foundingSupplies = Map.of("food", 8L);
        return Balance.of(raw);
    }

    /**
     * Two buildings: the hut (hosts forager, 1 slot, 1 point — completes on
     * tick 1 and unlocks work) and the bunker (nothing hosted, +10 food cap,
     * 10 points — completes on tick 2 with 5 builders, applying its bonus
     * from tick 3). Stock: 8 founding − 2 costs = 6.
     */
    private static Buildings hutAndBunker() {
        Buildings.Raw raw = new Buildings.Raw();
        raw.buildings = List.of(
                new Buildings.BuildingDef(
                        "hut", "forager", 1, List.of(new Buildings.CostEntry("food", 1L)),
                        1L, Boolean.TRUE, null, 0, Map.of()),
                new Buildings.BuildingDef(
                        "bunker", null, 0, List.of(new Buildings.CostEntry("food", 1L)),
                        10L, Boolean.TRUE, null, 0, Map.of("food", 10L)));
        return Buildings.of(raw, tinyBalance());
    }

    private static void startSyntheticTown(SimEngine engine) {
        engine.execute(new Command.Build("hut"));
        engine.execute(new Command.Build("bunker"));
        assertThat(Fixed.unitsFloor(engine.snapshot().stockpile().get("food"))).isEqualTo(6);
        engine.execute(new Command.AssignBuilders("hut-000001", 1));
        engine.execute(new Command.AssignBuilders("bunker-000002", 5));
        engine.tick(1); // hut complete (tick 1), bunker at 5/10
        engine.execute(new Command.AssignWorkers("forager", 1)); // hut's slot
    }

    /**
     * The re-segmentation core (docs/09 + DR-0003): a capacity bonus lands at
     * a completion boundary INSIDE a catch-up window; the closed form must
     * split the window at the boundary and be bit-identical to live play.
     * Numbers: stock 7 after the hut tick (6 + 1 produced), cap 10 through
     * tick 2's bunker completion, cap 20 from tick 3 → the fill transition
     * must fire on tick 15 (stock 7 at tick 2, 7 + (t − 2) = 20 at t = 15),
     * never earlier. Both paths must agree bit for bit INCLUDING events.
     */
    @Test
    void capacityBonusReSegmentsClosedFormExactly() {
        Balance balance = tinyBalance();

        SimEngine live = new SimEngine(balance, hutAndBunker(),
                GameState.initial(balance, TestSupport.SEED));
        startSyntheticTown(live);
        live.tick(19);

        SimEngine offline = new SimEngine(balance, hutAndBunker(),
                GameState.initial(balance, TestSupport.SEED));
        startSyntheticTown(offline);
        offline.catchUp(19);

        assertThat(SaveCodec.stateHash(offline.snapshot()))
                .as("cap re-segmentation must be bit-exact in the closed form")
                .isEqualTo(SaveCodec.stateHash(live.snapshot()));

        List<SimEvent.StockpileFull> fills = live.snapshot().events().stream()
                .filter(SimEvent.StockpileFull.class::isInstance)
                .map(SimEvent.StockpileFull.class::cast)
                .toList();
        assertThat(fills).hasSize(1);
        assertThat(fills.get(0).tick()).isEqualTo(15);
        assertThat(Fixed.unitsFloor(live.snapshot().stockpile().get("food")))
                .isEqualTo(20); // the BONUS cap, not the 10 base cap
    }

    @Test
    void townTierIsDerivedFromCompletedGrantsTier() {
        Balance balance = tinyBalance();
        Buildings.Raw raw = new Buildings.Raw();
        raw.buildings = List.of(new Buildings.BuildingDef(
                "keep", "forager", 0, List.of(new Buildings.CostEntry("food", 1L)),
                1L, Boolean.TRUE, null, 1, Map.of()));
        Buildings buildings = Buildings.of(raw, balance);

        SimEngine live = new SimEngine(balance, buildings,
                GameState.initial(balance, TestSupport.SEED));
        live.execute(new Command.Build("keep"));
        assertThat(live.townTier()).isZero(); // in-progress, not granted yet
        live.execute(new Command.AssignBuilders("keep-000001", 1));
        live.tick(1);
        assertThat(live.townTier()).isEqualTo(1); // highest completed grant wins

        SimEngine offline = new SimEngine(balance, buildings,
                GameState.initial(balance, TestSupport.SEED));
        offline.execute(new Command.Build("keep"));
        offline.execute(new Command.AssignBuilders("keep-000001", 1));
        offline.catchUp(1);
        assertThat(SaveCodec.stateHash(offline.snapshot()))
                .isEqualTo(SaveCodec.stateHash(live.snapshot()));
        assertThat(offline.townTier()).isEqualTo(1);
    }
}
