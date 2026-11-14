package com.nexus.campus.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the create-post tags array through the XSS wrapper.
 * When the wrapper rewrote JSON bodies with jsoup, array fields were the shape
 * most at risk of a mangling fallback; since ADR-0021 the wrapper stores JSON
 * bodies verbatim, so the array must survive byte-for-byte. (Note:
 * PostCreateRequest.tags expects List&lt;Integer&gt; tag IDs — a 400 on string
 * tag names is the documented API contract, not a wrapper issue; this test
 * pins the wrapper's own behavior.)
 */
class XssWrapperTagsArrayTest {

    @Test
    @DisplayName("tags array survives the wrapper byte-for-byte")
    void tagsArraySurvives() throws Exception {
        String body = "{\"title\":\"tags test\",\"categoryId\":2,"
                + "\"content\":\"ascii content\",\"tags\":[\"java\",\"x\"]}";
        assertEquals(body, roundTrip(body));
    }

    @Test
    @DisplayName("html in tag values is stored verbatim, not stripped or escaped")
    void htmlTagValueStoredVerbatim() throws Exception {
        String body = "{\"title\":\"t\",\"categoryId\":2,\"content\":\"c\","
                + "\"tags\":[\"<script>alert(1)</script>safe\"]}";
        String out = roundTrip(body);
        // Ingress is byte-faithful; the stored <script> is inert because egress
        // renders through react-markdown, which escapes raw HTML (ADR-0021).
        assertTrue(out.contains("<script>alert(1)</script>safe"));
    }

    private String roundTrip(String body) throws IOException {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.setContentType("application/json");
        request.setCharacterEncoding("UTF-8");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        BufferedReader reader = wrapper.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }
}
