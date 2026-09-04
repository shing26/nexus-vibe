package com.nexus.campus.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.web.util.HtmlUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Whitelist used for JSON request bodies: keeps safe formatting tags
     * (p, code, pre, lists, tables, links with http(s)/mailto, images with
     * http(s)) while stripping scripts, event handlers, javascript: URLs and
     * other dangerous constructs. Plain text (including Markdown such as
     * {@code a < b}) passes through unchanged, avoiding the double-encoding
     * caused by blanket HTML-escaping of user content.
     */
    private static final Safelist JSON_SAFELIST = Safelist.relaxed()
            .addTags("code", "pre", "table", "thead", "tbody", "tr", "th", "td")
            .addAttributes("a", "href", "title", "target")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addProtocols("img", "src", "http", "https");

    private static final Document.OutputSettings JSON_OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    private byte[] cachedBody;
    private boolean bodySanitized;

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.bodySanitized = false;
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
        ensureBodySanitized();
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        ensureBodySanitized();
        ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new BufferedReader(new InputStreamReader(bais, getCharacterEncodingOrDefault()));
    }

    private void ensureBodySanitized() throws IOException {
        if (bodySanitized) return;

        String contentType = super.getContentType();
        String encoding = getCharacterEncodingOrDefault();

        byte[] raw = readBodyBytes();
        if (raw == null || raw.length == 0) {
            cachedBody = raw;
            bodySanitized = true;
            return;
        }

        String body = new String(raw, encoding);

        if (isJsonContent(contentType)) {
            body = sanitizeJsonBody(body);
        } else {
            body = HtmlUtils.htmlEscape(body);
        }

        cachedBody = body.getBytes(encoding);
        bodySanitized = true;
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

    private String sanitizeJsonBody(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            sanitizeJsonNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return HtmlUtils.htmlEscape(body);
        }
    }

    private void sanitizeJsonNode(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> fields = obj.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                JsonNode child = obj.get(field);
                if (child.isTextual()) {
                    obj.put(field, sanitizeHtml(child.asText()));
                } else if (child.isObject() || child.isArray()) {
                    sanitizeJsonNode(child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                if (child.isTextual()) {
                    arr.set(i, new TextNode(sanitizeHtml(child.asText())));
                } else if (child.isObject() || child.isArray()) {
                    sanitizeJsonNode(child);
                }
            }
        }
    }

    /**
     * Whitelist-based sanitization for JSON body string values. Preserves
     * benign formatting and user content while removing dangerous HTML.
     */
    private String sanitizeHtml(String value) {
        if (value == null) return null;
        return Jsoup.clean(value, "", JSON_SAFELIST, JSON_OUTPUT_SETTINGS);
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