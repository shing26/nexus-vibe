package com.nexus.campus.ai.service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * First-pass semantic check. The application still runs its own gate; this one exists so
 * the service can spend one reinforced retry before returning a weak result.
 */
final class ReviewValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile("^[A-Z\\s]+$");
    private static final int MIN_FIELD_LENGTH = 20;
    private static final Set<String> SEVERITIES = Set.of("low", "medium", "high", "critical");

    private ReviewValidator() {
    }

    static boolean isValid(int score, String severity, String quality, String suggestions) {
        return score >= 0 && score <= 10
                && SEVERITIES.contains(severity)
                && substantive(quality)
                && substantive(suggestions);
    }

    private static boolean substantive(String field) {
        if (field == null) {
            return false;
        }
        String trimmed = field.trim();
        return trimmed.length() >= MIN_FIELD_LENGTH
                && !PLACEHOLDER.matcher(trimmed).matches();
    }
}
