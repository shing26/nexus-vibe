package com.nexus.campus.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the XSS wrapper's JSON-array sanitization: array
 * fields (e.g. create-post tag IDs) must survive as clean string arrays, not
 * trigger the html-escape fallback. (Note: PostCreateRequest.tags expects
 * List<Integer> tag IDs — a 400 on string tag names is the documented API
 * contract, not a wrapper issue; this test pins the wrapper's own behavior.)
 */
class XssWrapperTagsArrayTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode sanitize(String json) throws Exception {
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(
                new org.springframework.mock.web.MockHttpServletRequest());
        java.lang.reflect.Method m = XssHttpServletRequestWrapper.class
                .getDeclaredMethod("sanitizeJsonBody", String.class);
        m.setAccessible(true);
        String out = (String) m.invoke(wrapper, json);
        return mapper.readTree(out);
    }

    @Test
    @DisplayName("tags array survives sanitization as an array of clean strings")
    void tagsArraySurvives() throws Exception {
        String body = "{\"title\":\"tags test\",\"categoryId\":2,"
                + "\"content\":\"ascii content\",\"tags\":[\"java\",\"x\"]}";
        JsonNode out = sanitize(body);
        assertTrue(out.get("tags").isArray(), "tags must remain an array, got: " + out);
        assertEquals("java", out.get("tags").get(0).asText());
        assertFalse(out.toString().contains("&quot;"), "no double-escape fallback expected");
    }

    @Test
    @DisplayName("html in tag values is stripped, not escaped")
    void htmlTagValueStripped() throws Exception {
        String body = "{\"title\":\"t\",\"categoryId\":2,\"content\":\"c\","
                + "\"tags\":[\"<script>alert(1)</script>safe\"]}";
        JsonNode out = sanitize(body);
        assertEquals("safe", out.get("tags").get(0).asText());
    }
}
