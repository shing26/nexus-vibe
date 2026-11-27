package com.nexus.campus.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.campus.ai.api.dto.ReviewResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies the model's evidence quotes against the text it was asked to review.
 *
 * <p>A quote is matched after collapsing every whitespace run to one space, so
 * a model that reflows a line still matches. The returned span is the source's
 * own text, not the model's copy, and the line numbers are 1-based positions in
 * that source. Content is searched first, then each snippet; a snippet's line
 * numbers are relative to the snippet.</p>
 */
public final class EvidenceExtractor {

    static final int MIN_QUOTE_CHARS = 10;
    static final int MAX_QUOTES = 5;

    private EvidenceExtractor() {
    }

    public record Result(List<ReviewResponse.Evidence> accepted, int rejected) {
    }

    public static Result extract(JsonNode node, String content, List<String> snippets) {
        JsonNode evidence = node == null ? null : node.get("evidence");
        if (evidence == null || !evidence.isArray()) {
            return new Result(List.of(), 0);
        }

        List<String> sources = new ArrayList<>();
        if (content != null && !content.isBlank()) {
            sources.add(content);
        }
        if (snippets != null) {
            for (String snippet : snippets) {
                if (snippet != null && !snippet.isBlank()) {
                    sources.add(snippet);
                }
            }
        }

        List<ReviewResponse.Evidence> accepted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int rejected = 0;
        for (JsonNode item : evidence) {
            if (!item.isTextual()) {
                continue;
            }
            String normalized = normalize(item.asText());
            if (normalized.length() < MIN_QUOTE_CHARS || !seen.add(normalized)) {
                rejected++;
                continue;
            }
            ReviewResponse.Evidence located = locate(normalized, sources);
            if (located == null || accepted.size() >= MAX_QUOTES) {
                rejected++;
                continue;
            }
            accepted.add(located);
        }
        return new Result(List.copyOf(accepted), rejected);
    }

    private static ReviewResponse.Evidence locate(String quote, List<String> sources) {
        for (String source : sources) {
            Normalized normalized = normalizeWithPositions(source);
            int index = normalized.text().indexOf(quote);
            if (index < 0) {
                continue;
            }
            int start = normalized.positions().get(index);
            int end = normalized.positions().get(index + quote.length() - 1);
            return new ReviewResponse.Evidence(
                    source.substring(start, end + 1), lineOf(source, start), lineOf(source, end));
        }
        return null;
    }

    static String normalize(String value) {
        return normalizeWithPositions(value == null ? "" : value).text();
    }

    private static Normalized normalizeWithPositions(String value) {
        StringBuilder text = new StringBuilder(value.length());
        List<Integer> positions = new ArrayList<>(value.length());
        boolean pendingSpace = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                pendingSpace = !text.isEmpty();
                continue;
            }
            if (pendingSpace) {
                text.append(' ');
                positions.add(index - 1);
                pendingSpace = false;
            }
            text.append(current);
            positions.add(index);
        }
        return new Normalized(text.toString(), positions);
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private record Normalized(String text, List<Integer> positions) {
    }
}
