package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Repairing parser for LLM-produced JSON (fenced output, trailing commas,
 * max-token truncation). Pure function — no I/O, no retries.
 *
 * <p>Pipeline: extract the JSON span (first unmatched '{' to its matching
 * '}', which also drops markdown fences and prose), strip trailing commas,
 * then complete unclosed brackets/strings with a bracket-aware stack walk so
 * a response cut off by the token limit still parses into an object.</p>
 */
public final class JsonRepairUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonRepairUtil() {
    }

    /** Parse result: either a node or a positional error for self-correction. */
    public record ParseResult(JsonNode node, String error) {
        public boolean ok() {
            return node != null;
        }
    }

    public static ParseResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParseResult(null, "empty response");
        }
        String span = extractSpan(raw);
        for (String candidate : new String[]{span, stripTrailingCommas(span), completeBrackets(span)}) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                return new ParseResult(MAPPER.readTree(candidate), null);
            } catch (Exception ignored) {
                // try the next repair stage
            }
        }
        return new ParseResult(null, locateError(span));
    }

    /**
     * First '{' ... its matching '}' — implicitly drops ```json fences and
     * any surrounding prose the model added.
     */
    private static String extractSpan(String raw) {
        int start = raw.indexOf('{');
        if (start < 0) {
            return raw.trim();
        }
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start, i + 1);
                }
            }
        }
        // unterminated: return everything from the first brace (repair stage handles it)
        return raw.substring(start);
    }

    /** Removes trailing commas before } or ] (a common local-model artifact). */
    private static String stripTrailingCommas(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                sb.append(c);
                if (c == '\\') {
                    if (i + 1 < json.length()) {
                        sb.append(json.charAt(++i));
                    }
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(c);
            } else if (c == ',') {
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                    continue; // drop the comma
                }
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Truncation repair. Single forward pass tracking the bracket stack,
     * string state, and {@code lastCut} — the index just after the last
     * completed value/separator (a comma, an opener, a closing bracket, a
     * closed string, or a complete literal). A tail that ends past lastCut is
     * an unfinished value: the string is closed (or the partial literal is
     * dropped) and the bracket stack is closed out.
     */
    private static String completeBrackets(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 8);
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        int lastCut = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            sb.append(c);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                    lastCut = sb.length();
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                stack.push(c == '{' ? '}' : ']');
                lastCut = sb.length();
            } else if (c == '}' || c == ']') {
                stack.poll();
                lastCut = sb.length();
            } else if (c == ',') {
                lastCut = sb.length();
            } else if (Character.isDigit(c) || endsWithCompleteLiteral(sb)) {
                lastCut = sb.length();
            }
        }

        if (inString) {
            // close the dangling string value
            if (escaped && !sb.isEmpty()) {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append('"');
        } else if (lastCut < sb.length()) {
            // unfinished value (partial literal / dangling ':' or ','): cut back
            sb.setLength(Math.max(lastCut, 0));
        }

        // close the remaining bracket stack
        java.util.Deque<Character> reopen = new java.util.ArrayDeque<>();
        inString = false;
        escaped = false;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') reopen.push('}');
            else if (c == '[') reopen.push(']');
            else if (c == '}' || c == ']') reopen.poll();
        }
        if (inString) {
            sb.append('"');
        }
        while (!reopen.isEmpty()) {
            sb.append(reopen.pop());
        }
        return sb.toString();
    }

    /** True when the builder currently ends with true / false / null. */
    private static boolean endsWithCompleteLiteral(StringBuilder sb) {
        String s = sb.toString();
        return s.endsWith("true") || s.endsWith("fals") || s.endsWith("false")
                || s.endsWith("nul") || s.endsWith("null");
    }

    /** Positional error description used by the self-correction retry. */
    private static String locateError(String span) {
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < span.length(); i++) {
            char c = span.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                stack.push('}');
            } else if (c == '[') {
                stack.push(']');
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty() || stack.poll() != c) {
                    return "unmatched '" + c + "' near index " + i;
                }
            }
        }
        if (inString) {
            return "unterminated string at end of output";
        }
        if (!stack.isEmpty()) {
            return "unclosed '" + stack.peek() + "' at end of output (likely max-token truncation)";
        }
        return "malformed JSON, length=" + span.length();
    }

}
