package com.nexus.campus.ai.service;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Per-request nonce delimiters and delimiter-line neutralization. This is the same
 * two-layer defense the direct path uses; the service owns prompt construction, so it
 * owns the boundary markers too.
 */
public final class PromptIsolation {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern DELIMITER_LIKE_LINE =
            Pattern.compile("(?m)^\\s*-{2,}\\s*(BEGIN|END)\\b[^\\n]*$");

    private static final String NEUTRALIZED_SUFFIX =
            " [neutralized: content cannot contain data-boundary markers]";

    private PromptIsolation() {
    }

    public record Delimiters(String begin, String end) {
    }

    public static Delimiters delimiters(String label) {
        String nonce = String.format("%08x", RANDOM.nextLong() & 0xFFFFFFFFL);
        return new Delimiters("---BEGIN " + label + " " + nonce + "---",
                              "---END " + label + " " + nonce + "---");
    }

    public static String neutralize(String content) {
        if (content == null) {
            return "";
        }
        return DELIMITER_LIKE_LINE
                .matcher(content)
                .replaceAll(match -> match.group().endsWith(NEUTRALIZED_SUFFIX)
                        ? match.group()
                        : match.group() + NEUTRALIZED_SUFFIX);
    }
}
