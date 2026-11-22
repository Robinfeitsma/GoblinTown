package dev.goblintown.sim;

/**
 * One building instance in game state (docs/09): an abstract slot — no map
 * position in Phase 2a. Plain data (rules/02 §1); the engine applies all
 * logic. Part of GameState → saved, hashed, restored (H4).
 *
 * @param id       stable instance id ({@code type-serial}), the key players
 *                 assign builders to and events reference
 * @param type     building type id from data/buildings.json
 * @param complete false while the site is under construction
 * @param progress progress points accrued so far (whole units; builders add
 *                 1 per tick); completes at the type's {@code progressCost}
 */
public record Building(String id, String type, boolean complete, long progress) {

    public Building withProgress(long newProgress) {
        return new Building(id, type, complete, newProgress);
    }

    public Building completed() {
        return new Building(id, type, true, progress);
    }
}
