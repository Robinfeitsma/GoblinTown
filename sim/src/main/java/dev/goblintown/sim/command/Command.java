package dev.goblintown.sim.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Player/intent input to the simulation (M3 boundary: everything crossing
 * into the sim is a serializable record command). There is no other way to
 * mutate game state (rules/02 §1: presentation never mutates state directly).
 *
 * <p>Determinism contract (H1): the simulation result is a pure function of
 * the initial state and the ordered command list. Same commands, same order,
 * same state → same world, byte for byte.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(Command.AssignWorkers.class),
        @JsonSubTypes.Type(Command.UnassignWorkers.class)
})
public sealed interface Command {

    /** Assign {@code count} additional idle workers to {@code roleId}. */
    record AssignWorkers(String roleId, int count) implements Command {
        public AssignWorkers {
            if (roleId == null || roleId.isBlank()) {
                throw new IllegalArgumentException("roleId required");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive: " + count);
            }
        }
    }

    /** Unassign {@code count} workers from {@code roleId} (clamped at 0 —
     *  over-assignment is a caller bug, not a sim error). */
    record UnassignWorkers(String roleId, int count) implements Command {
        public UnassignWorkers {
            if (roleId == null || roleId.isBlank()) {
                throw new IllegalArgumentException("roleId required");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive: " + count);
            }
        }
    }
}
