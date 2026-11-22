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
        @JsonSubTypes.Type(Command.UnassignWorkers.class),
        @JsonSubTypes.Type(Command.Build.class),
        @JsonSubTypes.Type(Command.CancelSite.class),
        @JsonSubTypes.Type(Command.AssignBuilders.class),
        @JsonSubTypes.Type(Command.UnassignBuilders.class)
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

    /** Starts a construction site of {@code buildingType} — validates
     *  buildability and affordability, deducts the full cost upfront
     *  (docs/09: sites never starve, offline math stays closed-form). */
    record Build(String buildingType) implements Command {
        public Build {
            if (buildingType == null || buildingType.isBlank()) {
                throw new IllegalArgumentException("buildingType required");
            }
        }
    }

    /** Cancels an in-progress site; the deducted cost is refunded in full
     *  (docs/09 OQ-2). Completing this site is an error. */
    record CancelSite(String buildingId) implements Command {
        public CancelSite {
            if (buildingId == null || buildingId.isBlank()) {
                throw new IllegalArgumentException("buildingId required");
            }
        }
    }

    /** Allocates {@code count} goblins to build on site {@code buildingId}
     *  (builder = labor mode, docs/09 §Worker accounting). */
    record AssignBuilders(String buildingId, int count) implements Command {
        public AssignBuilders {
            if (buildingId == null || buildingId.isBlank()) {
                throw new IllegalArgumentException("buildingId required");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive: " + count);
            }
        }
    }

    /** Pulls {@code count} goblins off site {@code buildingId} (clamped at
     *  0 — over-allocation is a caller bug, not a sim error). */
    record UnassignBuilders(String buildingId, int count) implements Command {
        public UnassignBuilders {
            if (buildingId == null || buildingId.isBlank()) {
                throw new IllegalArgumentException("buildingId required");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive: " + count);
            }
        }
    }
}
