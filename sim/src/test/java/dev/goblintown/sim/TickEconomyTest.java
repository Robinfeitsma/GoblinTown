package dev.goblintown.sim;

import dev.goblintown.sim.command.Command;
import dev.goblintown.sim.event.SimEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase-1 tick economy mechanics (rules/03 category 5 unit tests):
 * production, capping, level-ups, assignments. Expected values are derived
 * from the design docs' *structure* (docs/03 §Production model) and the data
 * table — the numbers themselves are PLACEHOLDER until the balance doc.
 */
class TickEconomyTest {

    @Test
    void producerAccumulatesPerTickExactly() {
        Balance balance = TestSupport.balance();
        Balance.RoleDef gatherer = balance.role("gatherer");
        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("gatherer", 1));

        int ticks = 600; // 10 seconds at 60 Hz, below the wood cap
        engine.tick(ticks);

        // Phase-1 production is linear: stockpile = founding supply +
        // per-tick gain × ticks, exactly (fixed-point addition never
        // truncates; DR-0003). The 450-wood founding supply is docs/09.
        long expected = Fixed.fromUnits(balance.foundingSupply("wood"))
                + gatherer.amountPerTickFixed() * ticks;
        assertThat(engine.snapshot().stockpile().get("wood")).isEqualTo(expected);
        assertThat(engine.snapshot().tick()).isEqualTo(ticks);
    }

    @Test
    void stockpileCapsWithExactlyOneFullEvent() {
        Balance balance = TestSupport.balance();
        Balance.ResourceDef wood = balance.resource("wood");
        long cap = wood.capFixed();
        long initial = Fixed.fromUnits(balance.foundingSupply("wood"));

        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("gatherer", 25)); // fast fill

        int ticks = 100_000; // far beyond the fill horizon
        engine.tick(ticks);

        GameState end = engine.snapshot();
        assertThat(end.stockpile().get("wood")).isEqualTo(cap);

        long fullEvents = end.events().stream()
                .filter(e -> e instanceof SimEvent.StockpileFull)
                .filter(e -> ((SimEvent.StockpileFull) e).resourceId().equals("wood"))
                .count();
        assertThat(fullEvents).as("exactly one 'full' transition, not a flood").isEqualTo(1);

        // The event's tick must be the FIRST tick where stock ≥ cap:
        // t = ceil((cap − supply) / gain), because stock grows by `gain`
        // per tick from the founding supply.
        long gain = 25L * balance.role("gatherer").amountPerTickFixed();
        long expectedFillTick = Fixed.ceilDiv(cap - initial, gain);
        long actual = end.events().stream()
                .filter(e -> e instanceof SimEvent.StockpileFull)
                .mapToLong(SimEvent::tick)
                .findFirst().orElseThrow();
        assertThat(actual).isEqualTo(expectedFillTick);

        // After capping, production is flat — no growth past the cap.
        long tickAtCap = end.tick();
        engine.tick(1_000);
        assertThat(engine.snapshot().stockpile().get("wood")).isEqualTo(cap);
        assertThat(engine.snapshot().tick()).isEqualTo(tickAtCap + 1_000);
    }

    @Test
    void roleLevelsUpAtTheExactDerivedTick() {
        Balance balance = TestSupport.balance();
        Balance.RoleDef miner = balance.role("miner");
        int workers = 3;
        long gain = (long) workers * miner.xpPerTickFixed();

        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.execute(new Command.AssignWorkers("miner", workers));

        // one tick before the first threshold must NOT have fired level-up
        long threshold1 = miner.levelThresholdFixed(1);
        long firstLevelTick = Fixed.ceilDiv(threshold1, gain);
        engine.tick((int) (firstLevelTick - 1));
        assertThat(engine.snapshot().events())
                .as("no level-up before the threshold")
                .noneMatch(e -> e instanceof SimEvent.RoleLevelUp);

        engine.tick(1);
        assertThat(engine.snapshot().events())
                .containsExactly(new SimEvent.RoleLevelUp(firstLevelTick, "miner", 2));
        assertThat(engine.snapshot().roleLevel().get("miner")).isEqualTo(2);
    }

    @Test
    void unassignClampsAtZeroAndZeroWorkersProduceNothing() {
        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        engine.execute(new Command.UnassignWorkers("gatherer", 5)); // over-unassign
        assertThat(engine.snapshot().assignments().get("gatherer")).isEqualTo(0);

        engine.tick(1_000); // no guidance doc says idle workers tick anything yet
        GameState end = engine.snapshot();
        // everything stays at its founding state (wood = founding supply)
        assertThat(end.stockpile().get("wood"))
                .isEqualTo(Fixed.fromUnits(TestSupport.balance().foundingSupply("wood")));
        assertThat(end.stockpile().get("food")).isEqualTo(0);
        assertThat(end.stockpile().get("stone")).isEqualTo(0);
        assertThat(end.events()).isEmpty();
        assertThat(end.roleLevel()).allSatisfy((id, level) -> assertThat(level).isEqualTo(1));
    }

    @Test
    void rateConversionMatchesDesignDocStructure() {
        Balance balance = TestSupport.balance();
        // docs/03 §Formulas / docs/08 (approved): per-tick = whole-units-per-
        // second / 60, truncated once at load (DR-0003) — the sim never
        // re-derives it per tick.
        assertThat(balance.role("gatherer").amountPerTickFixed())
                .isEqualTo(Fixed.fromUnits(3) / Balance.TICKS_PER_SECOND);
        assertThat(balance.resource("stone").capFixed())
                .isEqualTo(Fixed.fromUnits(600));
    }

    @Test
    void invalidCommandsAreRejectedAtTheBoundary() {
        SimEngine engine = TestSupport.newGame(TestSupport.SEED);
        assertThatThrownBy(() -> engine.execute(new Command.AssignWorkers("gatherer", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.execute(new Command.AssignWorkers("no-such-role", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balanceLoaderRejectsBrokenTables() {
        Balance.Raw broken = new Balance.Raw();
        broken.resources = List.of(new Balance.ResourceDef("wood", 100));
        broken.roles = List.of(new Balance.RoleDef("gatherer", "ghost", 1, 1, 10, 0));
        assertThatThrownBy(() -> Balance.of(broken))
                .as("role producing unknown resource must fail at load")
                .isInstanceOf(IllegalArgumentException.class);

        Balance.Raw duplicate = new Balance.Raw();
        duplicate.resources = List.of(
                new Balance.ResourceDef("wood", 100),
                new Balance.ResourceDef("wood", 200));
        duplicate.roles = List.of(new Balance.RoleDef("gatherer", "wood", 1, 1, 10, 0));
        assertThatThrownBy(() -> Balance.of(duplicate))
                .as("duplicate resource ids must fail at load")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
