package com.nexus.campus.agent;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Per-request nonce delimiters for LLM prompt data isolation
 * (docs/blog/llm-code-review-structured-output-injection-defense.md).
 *
 * <p>Post content is untrusted user input. Fixed delimiters such as
 * {@code ---END CODE---} can be forged inside the content itself, letting a
 * user close the data region early and smuggle instructions to the model.
 * Two layers defend against that:</p>
 * <ol>
 *   <li><b>Nonce markers</b> — each request gets unpredictable markers that
 *   the system prompt declares verbatim; user content cannot predict them.</li>
 *   <li><b>Neutralization</b> — content lines that look like any delimiter
 *   ({@code ---BEGIN ...}/{@code ---END ...}) are suffixed with an annotation
 *   so they can never be mistaken for a real boundary, whatever the markers.</li>
 * </ol>
 */
public final class PromptIsolation {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern DELIMITER_LIKE_LINE =
            Pattern.compile("(?m)^\\s*-{2,}\\s*(BEGIN|END)\\b[^\\n]*$");

    private PromptIsolation() {
    }

    /**
     * A matched begin/end marker pair sharing one per-request nonce, e.g.
     * {@code ---BEGIN CODE 3fa9c1d2---} / {@code ---END CODE 3fa9c1d2---}.
     */
    public record Delimiters(String begin, String end) {
    }

    public static Delimiters delimiters(String label) {
        String nonce = randomNonce();
        return new Delimiters("---BEGIN " + label + " " + nonce + "---",
                              "---END " + label + " " + nonce + "---");
    }

    /**
     * Neutralizes delimiter-like lines in untrusted content so they cannot
     * terminate a data region early, even if a nonce ever leaked.
     */
    public static String neutralize(String content) {
        if (content == null) {
            return null;
        }
        return DELIMITER_LIKE_LINE
                .matcher(content)
                .replaceAll(match -> match.group() + " [neutralized: content cannot contain data-boundary markers]");
    }

    private static String randomNonce() {
        return String.format("%08x", RANDOM.nextLong() & 0xFFFFFFFFL);
    }
}
