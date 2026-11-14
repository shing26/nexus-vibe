package com.nexus.campus.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link XssHttpServletRequestWrapper}.
 *
 * <p>Verifies that URL parameters and headers are HTML-escaped (the reflected
 * path) and that JSON request bodies are passed through verbatim — since
 * ADR-0021 the storage layer performs no HTML rewriting of body content; the
 * XSS defense for that content lives at egress (react-markdown escapes by
 * default), not at ingress.</p>
 */
class XssFilterTest {

    @Test
    @DisplayName("HTML tags in URL parameters are escaped")
    void parameterValuesShouldBeEscaped() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("content")).thenReturn("<script>alert(\"xss\")</script>");
        when(request.getParameter("name")).thenReturn("Hello, World!");

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);

        assertEquals("&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;",
                wrapper.getParameter("content"));
        assertEquals("Hello, World!", wrapper.getParameter("name"));
    }

    @Test
    @DisplayName("Parameter array values are all escaped")
    void parameterArrayValuesShouldBeEscaped() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterValues("tags"))
                .thenReturn(new String[]{"<b>bold</b>", "normal", "<script>evil()</script>"});

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        String[] escaped = wrapper.getParameterValues("tags");

        assertNotNull(escaped);
        assertEquals("&lt;b&gt;bold&lt;/b&gt;", escaped[0]);
        assertEquals("normal", escaped[1]);
        assertEquals("&lt;script&gt;evil()&lt;/script&gt;", escaped[2]);
    }

    @Test
    @DisplayName("Parameter map returns unmodifiable escaped values")
    void parameterMapShouldBeEscaped() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(
                Map.of("input", new String[]{"<script>xss</script>"})
        );

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        Map<String, String[]> map = wrapper.getParameterMap();

        assertThrows(UnsupportedOperationException.class, () -> map.put("k", new String[]{"v"}));
        assertEquals("&lt;script&gt;xss&lt;/script&gt;", map.get("input")[0]);
    }

    @Test
    @DisplayName("Header values are escaped")
    void headerValuesShouldBeEscaped() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("<script>malicious</script>");
        when(request.getHeader("Referer")).thenReturn("http://normal-site.com");

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);

        assertEquals("&lt;script&gt;malicious&lt;/script&gt;", wrapper.getHeader("User-Agent"));
        assertEquals("http://normal-site.com", wrapper.getHeader("Referer"));
    }

    @Test
    @DisplayName("Normal text in parameters is unaffected")
    void normalTextShouldRemainUnchanged() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("greeting")).thenReturn("Hello, World!");

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);

        assertEquals("Hello, World!", wrapper.getParameter("greeting"));
    }

    @Test
    @DisplayName("JSON body is stored verbatim, including a script tag (safety lives at egress)")
    void jsonBodyShouldBeStoredVerbatim() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("application/json");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");

        String json = "{\"title\":\"<script>alert(1)</script>\",\"body\":\"safe text\"}";
        when(request.getInputStream()).thenReturn(streamOf(json));

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        String resultBody = readViaInputStream(wrapper);

        // No HTML rewriting at ingress, <script> included: the storage layer is
        // byte-faithful. A stored <script> is inert because egress renders it
        // through react-markdown, which escapes raw HTML by default (ADR-0021).
        assertEquals(json, resultBody);
        assertTrue(resultBody.contains("<script>alert(1)</script>"));
        assertTrue(resultBody.contains("\"body\":\"safe text\""));
    }

    @Test
    @DisplayName("JSON body keeps markdown, comparisons and generics untouched")
    void jsonBodyShouldKeepMarkdownAndComparisons() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("application/json");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");

        String json = "{\"content\":\"# Title **bold** `code`\\n\\n```java\\nif (a < b && c > d) { }\\nMap<String, Value> m;\\n```\\n\\na &amp; b\"}";
        when(request.getInputStream()).thenReturn(streamOf(json));

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        String resultBody = readViaReader(wrapper);

        // Markdown structure, comparison operators, generics and even a
        // user-typed entity all survive exactly as sent — nothing is escaped,
        // normalized or stripped before storage.
        assertEquals(json, resultBody);
    }

    @Test
    @DisplayName("Non-JSON body is fully escaped as plain text")
    void nonJsonBodyShouldBeEscaped() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("text/plain");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");

        String body = "<p>Hello <b>World</b></p>";
        when(request.getInputStream()).thenReturn(streamOf(body));

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        String escaped = readViaReader(wrapper);

        assertTrue(escaped.contains("&lt;p&gt;"));
        assertTrue(escaped.contains("&lt;b&gt;"));
        assertTrue(escaped.contains("&lt;/b&gt;"));
        assertTrue(escaped.contains("&lt;/p&gt;"));
        assertFalse(escaped.contains("<p>"));
    }

    private ServletInputStream streamOf(String body) {
        ByteArrayInputStream bais = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override public int read() throws IOException { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };
    }

    private String readViaInputStream(XssHttpServletRequestWrapper wrapper) throws IOException {
        return new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String readViaReader(XssHttpServletRequestWrapper wrapper) throws IOException {
        BufferedReader reader = wrapper.getReader();
        return reader.lines().reduce("", (a, b) -> a + b);
    }
}
