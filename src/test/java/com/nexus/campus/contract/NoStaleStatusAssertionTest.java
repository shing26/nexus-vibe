package com.nexus.campus.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eleven assertions across four test files used to pair {@code status().isOk()}
 * with a body that said 401, 403 or 404. They were not wrong - they described
 * the deployment exactly - they just pinned the behaviour this ticket exists to
 * remove, and one of them sat in a method called
 * {@code loginWrongPassword_shouldReturn401}.
 *
 * <p>So the rule is scanned rather than reviewed: no test may claim a 200 while
 * asserting a non-200 envelope code. It reads the test sources the way
 * {@code NoEntityInControllerTest} reads handler signatures - a check that fails
 * on a future edit, which is the only kind of check that survives an edit.</p>
 */
class NoStaleStatusAssertionTest {

    private static final Pattern CODE_ASSERTION =
            Pattern.compile("jsonPath\\(\"\\$\\.code\",\\s*is\\((\\d+)\\)\\)");
    private static final Pattern OK_ASSERTION = Pattern.compile("status\\(\\)\\.isOk\\(\\)");
    private static final Pattern STATEMENT_START =
            Pattern.compile("mockMvc\\.perform|@Test|\\bprivate\\s|\\bvoid\\s");

    @Test
    @DisplayName("No test asserts HTTP 200 alongside a non-200 envelope code")
    void noAssertionPinsATwoHundredOntoAFailure() throws IOException {
        Path root = Paths.get("src", "test", "java");
        assertThat(Files.isDirectory(root))
                .as("this check needs the test sources on disk; run it through Maven")
                .isTrue();

        List<String> offenders = new ArrayList<>();
        int examined = 0;

        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher code = CODE_ASSERTION.matcher(lines.get(i));
                    if (!code.find() || "200".equals(code.group(1))) {
                        continue;
                    }
                    examined++;
                    String claimedCode = code.group(1);
                    for (int j = i; j >= 0; j--) {
                        if (OK_ASSERTION.matcher(lines.get(j)).find()) {
                            offenders.add(file.getFileName() + ":" + (i + 1)
                                    + " claims code " + claimedCode + " but expects 200 (line " + (j + 1) + ")");
                            break;
                        }
                        if (j < i && STATEMENT_START.matcher(lines.get(j)).find()) {
                            break;
                        }
                    }
                }
            }
        }

        assertThat(examined)
                .as("the scan has to actually see envelope assertions to mean anything")
                .isGreaterThan(20);
        assertThat(offenders).isEmpty();
    }
}
