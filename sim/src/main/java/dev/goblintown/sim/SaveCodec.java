package dev.goblintown.sim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Save path for the sim state (H4): versioned, human-readable JSON, canonical
 * serialization — stable field order, map entries sorted by key.
 *
 * <p>Canonical form double-serves as the determinism hash input (rules/03:
 * "hash = canonical serialization of full sim state"), so the determinism
 * tests and the save round-trip test exercise the same bytes (categories 1+3
 * intersect by design).
 *
 * <p>Rules/03 determinism-hash recipe: SHA-256 over the canonical JSON
 * (UTF-8, no whitespace) of the {@link SaveEnvelope} — version included, so
 * a save-version bump changes hashes and forces conscious migration work.
 */
public final class SaveCodec {

    /**
     * Bump on any state-shape change; migrations live in sim (rules/02:
     * the data model owns its save version). v1: Phase-1 tick economy.
     */
    public static final int SAVE_VERSION = 1;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // canonical output: sorted map entries, stable record field order,
            // no nulls — byte-stable input for the determinism hash
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    /** Versioned save envelope: the on-disk / on-the-wire save shape. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaveEnvelope(
            @JsonProperty("version") int version,
            @JsonProperty("state") GameState state) {
    }

    private SaveCodec() {
    }

    /** Canonical JSON of a versioned envelope (no whitespace, sorted keys). */
    public static String toCanonicalJson(GameState state) {
        try {
            return MAPPER.writeValueAsString(new SaveEnvelope(SAVE_VERSION, state));
        } catch (Exception e) {
            throw new IllegalStateException("save serialization failed", e);
        }
    }

    /** Parses a save produced by {@link #toCanonicalJson} (or any writer of
     *  the same schema); rejects unknown fields so corrupted saves fail loudly
     *  at load, not silently deep in gameplay. Rejects saves NEWER than this
     *  build's SAVE_VERSION (older versions get migrated, never silently
     *  accepted). */
    public static SaveEnvelope fromCanonicalJson(String json) {
        try {
            SaveEnvelope envelope = MAPPER.readValue(json, SaveEnvelope.class);
            if (envelope.version() > SAVE_VERSION) {
                throw new IllegalArgumentException("save version " + envelope.version()
                        + " is newer than this build supports (" + SAVE_VERSION + ")");
            }
            return envelope;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("save parse failed", e);
        }
    }

    /** SHA-256 (hex, lowercase) of the canonical save JSON — the sim-state
     *  hash used by every determinism test (rules/03 §Determinism test
     *  conventions). */
    public static String stateHash(GameState state) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    toCanonicalJson(state).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM without SHA-256", e);
        }
    }
}
