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
     * v2 (2026-11-22): adds the town layer — {@code buildings},
     * {@code builderAssignments}, {@code nextBuildingSerial} (docs/09);
     * v1 states migrate by adding an empty town (there were none).
     */
    public static final int SAVE_VERSION = 2;

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
     *  build's SAVE_VERSION; older versions get migrated (see
     *  {@code migration}), never silently accepted. The returned envelope
     *  keeps its ON-DISK version; re-saving migrates it. */
    public static SaveEnvelope fromCanonicalJson(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(json);
            int version = root.path("version").asInt(-1);
            if (version < 0) {
                throw new IllegalArgumentException("save has no usable version");
            }
            if (version > SAVE_VERSION) {
                throw new IllegalArgumentException("save version " + version
                        + " is newer than this build supports (" + SAVE_VERSION + ")");
            }
            com.fasterxml.jackson.databind.JsonNode stateNode = root.get("state");
            if (stateNode == null) {
                throw new IllegalArgumentException("save has no state");
            }
            GameState state = version < SAVE_VERSION ? migrate(stateNode, version)
                    : MAPPER.treeToValue(stateNode, GameState.class);
            return new SaveEnvelope(version, state);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("save parse failed", e);
        }
    }

    /**
     * The one place save migrations live (rules/02). v1 → current: the town
     * layer fields didn't exist yet; a v1 state IS a state with no buildings
     * (docs/09), so it loads with an empty town and serial 1.
     */
    private static GameState migrate(com.fasterxml.jackson.databind.JsonNode stateNode,
                                     int version) {
        if (version != 1) {
            throw new IllegalArgumentException("no migration for save version " + version);
        }
        com.fasterxml.jackson.databind.node.ObjectNode upgraded =
                stateNode.deepCopy();
        upgraded.set("buildings", MAPPER.createArrayNode());
        upgraded.set("builderAssignments", MAPPER.createObjectNode());
        upgraded.put("nextBuildingSerial", 1L);
        try {
            return MAPPER.treeToValue(upgraded, GameState.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("v1 save migration failed", e);
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

    // ------------------------------------------------------------------
    // gzip layer (H4: saves are written gzipped). Pure byte transforms —
    // actual file I/O belongs to the launcher (headless/desktop), never
    // here within the sim's deterministic core (rules/02 §Java).
    // ------------------------------------------------------------------

    /** Canonical JSON → gzip bytes (the exact on-disk save format). */
    public static byte[] toGzipBytes(GameState state) {
        try {
            var bytes = toCanonicalJson(state).getBytes(StandardCharsets.UTF_8);
            var out = new java.io.ByteArrayOutputStream();
            try (var gzip = new java.util.zip.GZIPOutputStream(out)) {
                gzip.write(bytes);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("save gzip failed", e);
        }
    }

    /** gzip bytes → parsed, version-checked envelope (inverse of
     *  {@link #toGzipBytes}). */
    public static SaveEnvelope fromGzipBytes(byte[] gzipped) {
        try (var gzIn = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gzipped))) {
            String json = new String(gzIn.readAllBytes(), StandardCharsets.UTF_8);
            return fromCanonicalJson(json);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("save gunzip failed", e);
        }
    }
}
