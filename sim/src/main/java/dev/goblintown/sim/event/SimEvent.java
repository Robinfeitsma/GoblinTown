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
        @JsonSubTypes.Type(SimEvent.StockpileFull.class),
        @JsonSubTypes.Type(SimEvent.RoleLevelUp.class)
})
public sealed interface SimEvent {

    /** Ticks executed by the engine up to (and including) this event. */
    long tick();

    /** A resource first reached (or overflowed into) its storage cap at
     *  {@code tick}. Emitted once per fill transition; the report shows
     *  "storage full since" (docs/03 edge cases). */
    record StockpileFull(long tick, String resourceId) implements SimEvent {
    }

    /** A role's worker pool completed level {@code newLevel - 1} and is now
     *  at {@code newLevel}. Emitted once per completed level. */
    record RoleLevelUp(long tick, String roleId, int newLevel) implements SimEvent {
    }
}
