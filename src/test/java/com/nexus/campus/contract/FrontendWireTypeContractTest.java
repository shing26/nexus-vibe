package com.nexus.campus.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The browser talks to JSON, not to Java. {@code JacksonConfig} serialises
 * {@code Long}/{@code long}/{@code BigInteger} through {@code ToStringSerializer},
 * so an id that is a number on the server arrives as a string in the SPA, while
 * an {@code Integer} counter arrives as a number.
 *
 * <p>Nothing in the build compares the two sides. {@code tsc} only sees the TS
 * declaration, {@code mvn test} only sees the Java one, and a field declared with
 * the wrong primitive type still renders on screen often enough to look correct
 * ({@code "12".toLocaleString()} works; a string id pasted into a URL works).
 * That is how {@code AiLogStats} carried Long counters typed as {@code number}
 * and {@code ChannelStats.postCount} disagreed with {@code DashboardPage}'s own
 * copy of the same interface.</p>
 *
 * <p>So this test reads both source trees and checks a table of
 * backend DTO field -> frontend field pairs. Add a row when an endpoint gains a
 * numeric field the frontend reads.</p>
 */
class FrontendWireTypeContractTest {

    private static final Path DTO_DIR = Paths.get(
            "src", "main", "java", "com", "nexus", "campus", "dto");
    private static final Path AI_LOG_CONTROLLER = Paths.get(
            "src", "main", "java", "com", "nexus", "campus",
            "controller", "AiLogController.java");
    private static final Path TYPES = Paths.get("frontend", "src", "types", "post.ts");
    private static final Path MESSAGES_PAGE = Paths.get(
            "frontend", "src", "pages", "MessagesPage.tsx");

    /** One wire field: where the server declares it, where the SPA declares it. */
    private record WireField(String dto, String javaField, Path tsFile, String tsInterface,
                             String tsField) {
    }

    private static final List<WireField> WIRE_FIELDS = List.of(
            new WireField("PostPageVo.java", "id", TYPES, "PostPageVo", "id"),
            new WireField("PostPageVo.java", "viewCount", TYPES, "PostPageVo", "viewCount"),
            new WireField("PostPageVo.java", "likeCount", TYPES, "PostPageVo", "likeCount"),
            new WireField("PostPageVo.java", "commentCount", TYPES, "PostPageVo", "commentCount"),
            new WireField("PostPageVo.java", "forkedFromId", TYPES, "PostPageVo", "forkedFromId"),
            new WireField("PostPageVo.java", "likedByMe", TYPES, "PostPageVo", "likedByMe"),
            new WireField("PageResult.java", "total", TYPES, "PageResponse", "total"),
            new WireField("ChannelStatsVo.java", "id", TYPES, "ChannelStats", "id"),
            new WireField("ChannelStatsVo.java", "postCount", TYPES, "ChannelStats", "postCount"),
            new WireField("MessageVo.java", "id", MESSAGES_PAGE, "Message", "id"),
            new WireField("MessageVo.java", "isRead", MESSAGES_PAGE, "Message", "isRead"));

    @Test
    @DisplayName("A frontend field agrees with the wire type its Java field serialises to")
    void frontendFieldsMatchTheirJavaWireType() throws IOException {
        for (WireField field : WIRE_FIELDS) {
            String javaType = javaFieldType(DTO_DIR.resolve(field.dto()), field.javaField());
            String tsType = tsFieldType(field.tsFile(), field.tsInterface(), field.tsField());

            assertThat(unionsOf(tsType))
                    .as("%s.%s is %s in Java, which is %s on the wire; %s declares %s: %s",
                            field.tsInterface(), field.tsField(), javaType,
                            wireType(javaType), field.tsFile().getFileName(), field.tsField(), tsType)
                    .contains(wireType(javaType));
        }
    }

    /**
     * {@code AiLogStats} comes from a {@code Map<String, Object>} of {@code long}
     * counters, so the whole interface is strings. The controller guard keeps this
     * row from passing after the endpoint stops being that map.
     */
    @Test
    @DisplayName("Agent log counters are typed as the strings the map serialises to")
    void agentLogStatsAreStrings() throws IOException {
        String controller = read(AI_LOG_CONTROLLER);
        assertThat(controller)
                .as("AiLogStats is only strings because this endpoint hands out a Map of longs")
                .contains("Map<String, Object> data")
                .contains("data.put(\"totalReviews\"");

        String body = interfaceBody(TYPES, "AiLogStats");
        Set<String> fields = new LinkedHashSet<>(tsFieldNames(body));
        assertThat(fields).as("AiLogStats should declare the counters").hasSizeGreaterThanOrEqualTo(8);
        for (String field : fields) {
            assertThat(tsFieldType(TYPES, "AiLogStats", field))
                    .as("AiLogStats.%s is a long on the wire, so it must be declared string", field)
                    .isEqualTo("string");
        }
    }

    /**
     * The reverse direction for the one interface that mirrors a DTO field for
     * field: a field the SPA reads but the server never sends is dead surface, and
     * is how {@code PostPageVo.isPinned} survived long after the column stopped
     * being returned.
     */
    @Test
    @DisplayName("Every PostPageVo field the SPA declares exists on the server DTO")
    void postPageVoDeclaresNoFieldsTheServerDoesNotSend() throws IOException {
        Set<String> serverFields = javaFieldNames(DTO_DIR.resolve("PostPageVo.java"));
        List<String> orphans = new ArrayList<>();
        for (String field : tsFieldNames(interfaceBody(TYPES, "PostPageVo"))) {
            if (!serverFields.contains(field)) {
                orphans.add("PostPageVo." + field);
            }
        }

        assertThat(orphans)
                .as("the SPA can only read fields PostPageVo actually serialises")
                .isEmpty();
    }

    // ---- wire rules ----

    private static String wireType(String javaType) {
        return switch (javaType) {
            case "Long", "long", "BigInteger", "String" -> "string";
            case "Integer", "int", "Short", "short", "Byte", "byte" -> "number";
            case "Boolean", "boolean" -> "boolean";
            default -> throw new IllegalArgumentException(
                    "no wire rule for " + javaType + "; add one before reading it in the SPA");
        };
    }

    /** Splits a TS union so `boolean | null` is judged on its boolean arm. */
    private static Set<String> unionsOf(String tsType) {
        Set<String> arms = new LinkedHashSet<>();
        for (String arm : tsType.split("\\|")) {
            arms.add(arm.trim());
        }
        return arms;
    }

    // ---- source reading ----

    private static String javaFieldType(Path dto, String field) throws IOException {
        String source = read(dto);
        Matcher matcher = Pattern.compile(
                        "(?m)^\\s*private\\s+([\\w.$\\[\\]]+)\\s+" + Pattern.quote(field) + "\\s*;")
                .matcher(source);
        assertThat(matcher.find())
                .as("%s should declare a field named %s", dto.getFileName(), field)
                .isTrue();
        return matcher.group(1);
    }

    private static Set<String> javaFieldNames(Path dto) throws IOException {
        Matcher matcher = Pattern.compile("(?m)^\\s*private\\s+[\\w.$<>\\[\\]]+\\s+(\\w+)\\s*;")
                .matcher(read(dto));
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String tsFieldType(Path file, String tsInterface, String field)
            throws IOException {
        Matcher matcher = Pattern.compile(
                        "(?m)^\\s*" + Pattern.quote(field) + "\\??\\s*:\\s*([^;]+);")
                .matcher(interfaceBody(file, tsInterface));
        assertThat(matcher.find())
                .as("%s should declare %s.%s", file.getFileName(), tsInterface, field)
                .isTrue();
        return matcher.group(1).trim();
    }

    private static List<String> tsFieldNames(String interfaceBody) {
        Matcher matcher = Pattern.compile("(?m)^\\s*(\\w+)\\??\\s*:").matcher(interfaceBody);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** The declared body of one TS interface, found by name rather than by line number. */
    private static String interfaceBody(Path file, String name) throws IOException {
        String source = read(file);
        Matcher matcher = Pattern.compile(
                        "(?m)^\\s*(?:export\\s+)?interface\\s+" + Pattern.quote(name)
                                + "(?:\\s*<[^>{}]*>)?\\s*\\{")
                .matcher(source);
        assertThat(matcher.find())
                .as("%s should declare interface %s", file.getFileName(), name)
                .isTrue();

        int depth = 1;
        int cursor = matcher.end();
        while (cursor < source.length() && depth > 0) {
            char c = source.charAt(cursor++);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        String body = source.substring(matcher.end(), cursor - 1);
        assertThat(body).as("interface %s should not be empty", name).isNotBlank();
        return body;
    }

    private static String read(Path path) throws IOException {
        assertThat(path).as("contract source %s", path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
