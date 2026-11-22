package dev.goblintown.sim.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Simulation output events (M3 boundary): compact, sequential records that
 * assemble the full ordered timeline of a return report (docs/03-idle-loop.md
 * §Return report, H2). Events are part of game state — saved, restored, and
 * byte-identical whether produced by live play or offline catch-up (H1,
 * rules/03: asserted structurally, never stringly).
 *
 * <p>Per-tick production intentionally emits NO events (timeline flood);
 * production shows up in stockpile deltas. Events mark *transitions*.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(SimEvent.BuildingFinished.class),
        @JsonSubTypes.Type(SimEvent.RoleLevelUp.class),
        @JsonSubTypes.Type(SimEvent.StockpileFull.class),
        @JsonSubTypes.Type(SimEvent.SiteCancelled.class)
})
public sealed interface SimEvent {

    /** Ticks executed by the engine up to (and including) this event. */
    long tick();

    /** A construction site reached its progressCost at {@code tick} and is
     *  now complete — slots/capacity bonuses apply from the NEXT production
     *  step in the same tick (construction runs before roles, docs/09). */
    record BuildingFinished(long tick, String buildingId, String buildingType) implements SimEvent {
    }

    /** A role's worker pool completed level {@code newLevel - 1} and is now
     *  at {@code newLevel}. Emitted once per completed level. */
    record RoleLevelUp(long tick, String roleId, int newLevel) implements SimEvent {
    }

    /** A resource first reached (or overflowed into) its storage cap at
     *  {@code tick}. Emitted once per fill transition; the report shows
     *  "storage full since" (docs/03 edge cases). */
    record StockpileFull(long tick, String resourceId) implements SimEvent {
    }

    /** An in-progress site was cancelled by command at {@code tick}; its
     *  full deducted cost was refunded (docs/09 OQ-2). Command-time event —
     *  not emitted by tickOnce. */
    record SiteCancelled(long tick, String buildingId) implements SimEvent {
    }
}
