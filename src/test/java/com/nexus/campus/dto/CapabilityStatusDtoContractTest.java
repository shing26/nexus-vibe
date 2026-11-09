package com.nexus.campus.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B2's guard: the response may not grow a field without a decision.
 *
 * <p>"A test fails if the DTO gains a field that is not on an explicit allow-list" is the
 * acceptance wording, and the allow-list is {@link #ALLOWED_FIELDS}. The endpoint exists to
 * replace the raw {@code deps} document with a shaped one; a field added casually here is how a
 * dependency name, status code or probe reason ends up on a public surface that ADR-0007 keeps
 * closed. The name check is a second net: even an allow-listed addition would fail here if it
 * were called {@code host}, {@code port}, {@code reason} or similar.</p>
 */
class CapabilityStatusDtoContractTest {

    /** Every field the public status response is allowed to carry. Add deliberately. */
    private static final Set<String> ALLOWED_FIELDS = Set.of("key", "impact");

    /** Words that describe a dependency rather than a user-visible consequence. */
    private static final List<String> FORBIDDEN_NAMES = List.of(
            "host", "hostname", "port", "url", "uri", "endpoint", "reason", "error", "exception",
            "stack", "trace", "component", "status", "detail", "dependency", "password", "secret");

    @Test
    @DisplayName("The DTO declares exactly the fields on the allow-list")
    void dtoFieldsAreExactlyTheAllowList() {
        assertThat(persistentFieldsOf(CapabilityStatusVo.class))
                .as("a new public field needs a decision and a line in ALLOWED_FIELDS")
                .isEqualTo(ALLOWED_FIELDS);
    }

    @Test
    @DisplayName("No field name describes a dependency instead of an impact")
    void fieldNamesAreAboutTheVisitor() {
        for (String name : persistentFieldsOf(CapabilityStatusVo.class)) {
            for (String forbidden : FORBIDDEN_NAMES) {
                assertThat(name.toLowerCase())
                        .as("%s is not a user-facing consequence", name)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("The JSON on the wire carries exactly key and impact")
    void serializedShapeIsTwoKeys() throws Exception {
        JsonNode json = new ObjectMapper()
                .readTree(new ObjectMapper().writeValueAsString(
                        new CapabilityStatusVo("search", "Search results may be incomplete.")));

        Set<String> fieldNames = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(fieldNames::add);

        assertThat(fieldNames).isEqualTo(ALLOWED_FIELDS);
        assertThat(json.get("key").asText()).isEqualTo("search");
    }

    private static Set<String> persistentFieldsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !f.isSynthetic() && !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
