package com.nexus.campus.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The ADR-0021 round-trip contract (frozen acceptance criterion 4): every
 * content shape a user may write — the six frozen inputs — must survive the
 * XSS wrapper byte-for-byte. The storage layer performs no HTML rewriting of
 * JSON request bodies, <em>including</em> an attack string like
 * {@code <script>alert(1)</script>}, which is stored raw on purpose: its
 * safety is guaranteed at egress, where the SPA renders content through
 * react-markdown and raw HTML is escaped by default. Escaping at ingress was
 * the defect this replaces — it turned {@code a < b} into
 * {@code a &lt; b} and deleted {@code Map<String, Value>} outright.
 */
class XssWrapperJsonBodyRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The six frozen inputs. Each is embedded in a JSON body through
     * Jackson's own escaping, so what the wrapper receives is exactly what a
     * real controller would receive.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "<",
            "&",
            "&&",
            "Map<String, Value>",
            "```java\nif (a < b && c > d) {\n    return \"x\";\n}\n```",
            "<script>alert(1)</script>"
    })
    @DisplayName("Every frozen input round-trips the wrapper byte-for-byte")
    void frozenInputsRoundTripByteForByte(String content) throws Exception {
        String json = MAPPER.writeValueAsString(
                java.util.Collections.singletonMap("content", content));

        String read = roundTrip(json);

        assertArrayEquals(json.getBytes(StandardCharsets.UTF_8),
                read.getBytes(StandardCharsets.UTF_8),
                "JSON body must reach the controller exactly as sent, got: " + read);
    }

    @Test
    @DisplayName("A script tag in a JSON body is stored raw — egress escapes it, not ingress")
    void scriptTagIsStoredRawNotEscaped() throws Exception {
        String json = "{\"content\":\"<script>alert(1)</script>\"}";

        String read = roundTrip(json);

        // Pins the ADR-0021 stance explicitly: no entry-point escaping, no
        // stripping. If this string ever comes back escaped (&lt;script&gt;)
        // or missing, the storage layer has started rewriting content again.
        assertEquals(json, read);
    }

    private String roundTrip(String body) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("application/json");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getInputStream()).thenReturn(streamOf(body));

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        // Read through both surfaces, as a controller might: the stream first,
        // then the reader — the cached body must be identical on every read.
        byte[] viaStream = wrapper.getInputStream().readAllBytes();
        String viaReader = new BufferedReader(
                new java.io.InputStreamReader(wrapper.getInputStream(), StandardCharsets.UTF_8))
                .lines().reduce("", (a, b) -> a + b);

        assertArrayEquals(viaStream, viaReader.getBytes(StandardCharsets.UTF_8),
                "stream and reader must see the same cached body");
        return new String(viaStream, StandardCharsets.UTF_8);
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
}
