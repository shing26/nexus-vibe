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
 * <p>Verifies that URL parameters and headers are HTML-escaped and JSON
 * request bodies are whitelist-sanitized before the application sees them.</p>
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
    @DisplayName("JSON body strips scripts and keeps plain text")
    void jsonBodyShouldBeSanitized() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("application/json");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");

        String json = "{\"title\":\"<script>alert(1)</script>\",\"body\":\"safe text\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bais = new ByteArrayInputStream(jsonBytes);

        ServletInputStream sis = new ServletInputStream() {
            @Override public int read() throws IOException { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };

        when(request.getInputStream()).thenReturn(sis);

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        ServletInputStream resultStream = wrapper.getInputStream();
        byte[] resultBytes = resultStream.readAllBytes();
        String resultBody = new String(resultBytes, StandardCharsets.UTF_8);

        // <script> element (with its payload) is removed entirely
        assertFalse(resultBody.contains("script"));
        assertFalse(resultBody.contains("alert"));
        assertTrue(resultBody.contains("\"body\":\"safe text\""));
    }

    @Test
    @DisplayName("JSON body keeps Markdown and normalizes entities once")
    void jsonBodyShouldKeepMarkdownAndNormalizeEntities() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("application/json");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");

        String json = "{\"content\":\"# Title **bold** `code` a &amp; b\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bais = new ByteArrayInputStream(jsonBytes);

        ServletInputStream sis = new ServletInputStream() {
            @Override public int read() throws IOException { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };

        when(request.getInputStream()).thenReturn(sis);

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        BufferedReader reader = wrapper.getReader();
        String resultBody = reader.lines().reduce("", (a, b) -> a + b);

        // Markdown structure survives
        assertTrue(resultBody.contains("# Title"));
        assertTrue(resultBody.contains("**bold**"));
        assertTrue(resultBody.contains("`code`"));
        // Entities are normalized exactly once, never double-encoded
        assertTrue(resultBody.contains("a &amp; b"));
        assertFalse(resultBody.contains("&amp;amp;"));
    }

    @Test
    @DisplayName("JSON body strips event handlers and javascript: URLs")
    void jsonBodyShouldStripDangerousAttributes() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("application/json");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");

        String json = "{\"content\":\"<img src=x onerror=alert(1)><a href=javascript:alert(1)>click</a><iframe src=x></iframe>\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bais = new ByteArrayInputStream(jsonBytes);

        ServletInputStream sis = new ServletInputStream() {
            @Override public int read() throws IOException { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };

        when(request.getInputStream()).thenReturn(sis);

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        BufferedReader reader = wrapper.getReader();
        String resultBody = reader.lines().reduce("", (a, b) -> a + b);

        assertFalse(resultBody.contains("onerror"));
        assertFalse(resultBody.contains("javascript:"));
        assertFalse(resultBody.contains("iframe"));
        assertTrue(resultBody.contains("<a>click</a>"));
    }

    @Test
    @DisplayName("Non-JSON body is fully escaped as plain text")
    void nonJsonBodyShouldBeEscaped() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("text/plain");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");

        String body = "<p>Hello <b>World</b></p>";
        ByteArrayInputStream bais = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));

        ServletInputStream sis = new ServletInputStream() {
            @Override public int read() throws IOException { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };

        when(request.getInputStream()).thenReturn(sis);

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        BufferedReader reader = wrapper.getReader();
        String escaped = reader.lines().reduce("", (a, b) -> a + b);

        assertTrue(escaped.contains("&lt;p&gt;"));
        assertTrue(escaped.contains("&lt;b&gt;"));
        assertTrue(escaped.contains("&lt;/b&gt;"));
        assertTrue(escaped.contains("&lt;/p&gt;"));
        assertFalse(escaped.contains("<p>"));
    }
}
