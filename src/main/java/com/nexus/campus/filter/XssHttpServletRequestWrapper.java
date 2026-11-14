package com.nexus.campus.filter;

import org.springframework.web.util.HtmlUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private byte[] cachedBody;
    private boolean bodyCached;

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.bodyCached = false;
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return sanitize(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) return null;
        String[] encoded = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            encoded[i] = sanitize(values[i]);
        }
        return encoded;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> raw = super.getParameterMap();
        Map<String, String[]> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : raw.entrySet()) {
            String[] values = entry.getValue();
            String[] encoded = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                encoded[i] = sanitize(values[i]);
            }
            sanitized.put(entry.getKey(), encoded);
        }
        return Collections.unmodifiableMap(sanitized);
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return sanitize(value);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        Enumeration<String> raw = super.getHeaders(name);
        List<String> sanitized = new ArrayList<>();
        while (raw.hasMoreElements()) {
            sanitized.add(sanitize(raw.nextElement()));
        }
        return Collections.enumeration(sanitized);
    }

    @Override
    public int getIntHeader(String name) {
        String value = super.getHeader(name);
        String safe = sanitize(value);
        if (safe == null || safe.isEmpty()) return -1;
        try {
            return Integer.parseInt(safe);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        ensureBodyCached();
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        ensureBodyCached();
        ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new BufferedReader(new InputStreamReader(bais, getCharacterEncodingOrDefault()));
    }

    /**
     * Caches the raw body once so it can be read repeatedly. JSON bodies are
     * stored byte-for-byte as the client sent them (ADR-0021): markdown and
     * code are content, not markup, and any rewrite here corrupts them before
     * storage. The XSS defense for body content lives at egress — the SPA
     * renders it through react-markdown, which escapes by default — not at
     * ingress. Non-JSON bodies keep the legacy escape-on-input.
     */
    private void ensureBodyCached() throws IOException {
        if (bodyCached) return;

        String contentType = super.getContentType();
        String encoding = getCharacterEncodingOrDefault();

        byte[] raw = readBodyBytes();
        if (raw == null || raw.length == 0) {
            cachedBody = raw;
            bodyCached = true;
            return;
        }

        String body = new String(raw, encoding);

        if (!isJsonContent(contentType)) {
            body = HtmlUtils.htmlEscape(body);
        }

        cachedBody = body.getBytes(encoding);
        bodyCached = true;
    }

    private byte[] readBodyBytes() throws IOException {
        ServletInputStream is = super.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            buffer.write(buf, 0, n);
        }
        return buffer.toByteArray();
    }

    private boolean isJsonContent(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.contains("application/json")
                || lower.contains("+json")
                || lower.contains("application/vnd.api+json");
    }

    /**
     * Escape-on-input for query parameters and headers (reflected-XSS defense).
     * These are short, non-content values, so blanket escaping is safe here.
     */
    private String sanitize(String value) {
        if (value == null) return null;
        return HtmlUtils.htmlEscape(value);
    }

    private String getCharacterEncodingOrDefault() {
        String enc = super.getCharacterEncoding();
        return (enc != null) ? enc : StandardCharsets.UTF_8.name();
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        CachedBodyServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body != null ? body : new byte[0]);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
        }
    }
}