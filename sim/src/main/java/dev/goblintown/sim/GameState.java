package dev.goblintown.sim;

import dev.goblintown.sim.event.SimEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The entire game state of the town layer (rules/02 §1: plain data, no
 * behavior that could hide rules; the engine applies all logic).
 *
 * <p>Serialization notes (H4):
 * <ul>
 *   <li>All resource amounts / XP pools are Q32.32 raw {@code long}s
 *       (DR-0003): divide by 4294967296 to read whole units.</li>
 *   <li>Maps are {@link LinkedHashMap}s built in table order; the canonical
 *       save serializes entries sorted by key, so field/entry order is
 *       stable in either case (determinism hashing).</li>
 *   <li>Instances are immutable snapshots produced by
 *       {@link SimEngine#snapshot()} — the only state shape that crosses a
 *       boundary (M3). The engine keeps its own working copies.</li>
 * </ul>
 *
 * @param tick               number of ticks executed so far (the only time in sim)
 * @param stockpile          resourceId → fixed amount
 * @param assignments        roleId → worker count
 * @param roleXp             roleId → fixed XP pool of that role's workers
 * @param roleLevel          roleId → current level (starts at 1)
 * @param rngState           seeded RNG state (SplitMix64); unused by the
 *                           Phase-1/2a economy but part of the state/hash
 *                           from day one so the determinism contract covers
 *                           it (H1)
 * @param events             ordered timeline events (docs/03 §Return report)
 * @param buildings          building instances in build order (docs/09);
 *                           victims of CancelSite are REMOVED — the list is
 *                           the live town, not a log
 * @param builderAssignments buildingId → goblin count currently building
 * @param nextBuildingSerial monotonically increasing serial for instance ids
 */
public record GameState(
        long tick,
        Map<String, Long> stockpile,
        Map<String, Integer> assignments,
        Map<String, Long> roleXp,
        Map<String, Integer> roleLevel,
        long rngState,
        List<SimEvent> events,
        List<Building> buildings,
        Map<String, Integer> builderAssignments,
        long nextBuildingSerial) {

    public static GameState initial(Balance balance, long seed) {
        LinkedHashMap<String, Long> stock = new LinkedHashMap<>();
        for (Balance.ResourceDef r : balance.resources()) {
            long supply = balance.foundingSupply(r.id());
            // a founding supply above capacity would create an instant
            // StockpileFull transition at tick 1 — clamp (validated at load too)
            stock.put(r.id(), Math.min(Fixed.fromUnits(supply), r.capFixed()));
        }
        LinkedHashMap<String, Integer> assigned = new LinkedHashMap<>();
        LinkedHashMap<String, Long> xp = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> level = new LinkedHashMap<>();
        for (Balance.RoleDef role : balance.roles()) {
            assigned.put(role.id(), 0);
            xp.put(role.id(), 0L);
            level.put(role.id(), 1);
        }
        return new GameState(0L, stock, assigned, xp, level, seed, new ArrayList<>(),
                new ArrayList<>(), new LinkedHashMap<>(), 1L);
    }

    /** Deep copy — used by tests and by the save/restore path. */
    public GameState deepCopy() {
        return new GameState(tick, new LinkedHashMap<>(stockpile), new LinkedHashMap<>(assignments),
                new LinkedHashMap<>(roleXp), new LinkedHashMap<>(roleLevel), rngState,
                new ArrayList<>(events), new ArrayList<>(buildings),
                new LinkedHashMap<>(builderAssignments), nextBuildingSerial);
    }
}
