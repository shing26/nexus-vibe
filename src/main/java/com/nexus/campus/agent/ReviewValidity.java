package com.nexus.campus.agent;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Review validity — the semantic check deciding whether a model's output is
 * usable at all. ADR-0012 keeps it in core rather than in any reviewer: "what
 * a verdict must satisfy before it may be recorded" is the project's argument,
 * so no replacement reviewer can weaken it by trusting its own output.
 *
 * <p>A schema-valid response from a weak model can still be placeholder
 * garbage ("EVALUATE", ""), so validity runs beside schema enforcement, not
 * instead of it.</p>
 */
final class ReviewValidity {

    /**
     * A field consisting only of uppercase words ("EVALUATE", "LOW") is a
     * schema-valid but semantically empty placeholder emitted by weak models.
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("^[A-Z\\s]+$");

    private static final int MIN_FIELD_LENGTH = 20;

    private static final Set<String> VALID_SEVERITIES = Set.of("low", "medium", "high", "critical");

    private ReviewValidity() {
    }

    static boolean isValid(Reviewer.Verdict verdict) {
        return verdict != null
                && VALID_SEVERITIES.contains(verdict.severity)
                && isSubstantive(verdict.quality)
                && isSubstantive(verdict.suggestions);
    }

    static boolean isSubstantive(String field) {
        if (field == null) {
            return false;
        }
        String trimmed = field.trim();
        return trimmed.length() >= MIN_FIELD_LENGTH
                && !PLACEHOLDER_PATTERN.matcher(trimmed).matches();
    }
}
