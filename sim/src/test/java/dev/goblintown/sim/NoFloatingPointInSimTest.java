package dev.goblintown.sim;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compiler-incomplete enforcement of H1 (no float/double in the sim ring) —
 * this test fails the build the moment a forbidden type lands in sim sources
 * (DR-0003 §Rationale: "a no-float rule check greps for float|double in sim").
 */
class NoFloatingPointInSimTest {

    private static final Pattern FORBIDDEN = Pattern.compile("\\b(float|double|Float|Double)\\b");

    @Test
    void simSourcesContainNoFloatingPointTypes() throws IOException {
        // Gradle runs tests with the working directory set to this module's
        // project dir (sim/), so src/ is the module source tree.
        Path sourceRoot = Path.of("src");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith("NoFloatingPointInSimTest.java"))
                    .forEach(file -> {
                try {
                    String source = Files.readString(file);
                    String[] lines = stripComments(source, file).split("\n", -1);
                    for (int i = 0; i < lines.length; i++) {
                        if (FORBIDDEN.matcher(lines[i]).find()) {
                            violations.add(file + ":" + (i + 1) + ": " + lines[i].trim());
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("cannot read " + file, e);
                }
            });
        }
        assertThat(violations)
                .as("float/double must never appear in sim (H1, DR-0003)")
                .isEmpty();
    }

    /**
     * Strips {@code //} line comments AND {@code /* … *\/} block comments
     * (javadoc legitimately discusses the rule — "no float/double" — so
     * scanning must look at code only). Crude but sound for our sources:
     * sim never holds string literals containing comment syntax.
     */
    private static String stripComments(String source, Path file) {
        String noBlock = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        String[] lines = noBlock.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            int comment = lines[i].indexOf("//");
            out.append(comment >= 0 ? lines[i].substring(0, comment) : lines[i]).append('\n');
        }
        return out.toString();
    }
}
