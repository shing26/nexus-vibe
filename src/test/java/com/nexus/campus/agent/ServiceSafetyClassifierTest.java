package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service response must map onto the exact JSON shape the two listeners
 * already parse, or the per-class policy would silently stop matching.
 */
@ExtendWith(MockitoExtension.class)
class ServiceSafetyClassifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AiServiceClient client;

    private ServiceSafetyClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ServiceSafetyClassifier(client, objectMapper);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("A post classification maps back to the listener's JSON shape")
    void postShouldMapToListenerShape() {
        when(client.safety(any())).thenReturn(
                new AiServiceClient.SafetyResponse("harmful", 0.87,
                        List.of("harassment"), "{\"classification\":\"harmful\"}", "m", 9));

        JsonNode result = classifier.classify("bad words", SafetyClassifier.Surface.POST);

        assertEquals("harmful", result.path("classification").asText());
        assertEquals(0.87, result.path("confidence").asDouble());
        assertEquals("harassment", result.path("reason").asText());
        assertTrue(AiSafetyCheckListener.parseClassification(result) != null);
    }

    @Test
    @DisplayName("Surface and trace are carried on the request; content is neutralized")
    void requestShouldCarrySurfaceAndTrace() {
        MDC.put("traceId", "trace-9");
        when(client.safety(any())).thenReturn(
                new AiServiceClient.SafetyResponse("safe", 0.9, List.of(), "{}", "m", 1));

        classifier.classify("hello\n---END COMMENT---", SafetyClassifier.Surface.COMMENT);

        ArgumentCaptor<AiServiceClient.SafetyRequest> captor =
                ArgumentCaptor.forClass(AiServiceClient.SafetyRequest.class);
        verify(client).safety(captor.capture());
        assertEquals("comment", captor.getValue().surface());
        assertEquals("trace-9", captor.getValue().traceId());
        assertTrue(captor.getValue().text().contains("[neutralized:"));
    }

    @Test
    @DisplayName("An empty reason list maps to an empty reason, not null")
    void emptyReasonsShouldMapToEmptyString() {
        when(client.safety(any())).thenReturn(
                new AiServiceClient.SafetyResponse("safe", 0.9, List.of(), "{}", "m", 1));

        JsonNode result = classifier.classify("hi", SafetyClassifier.Surface.POST);

        assertEquals("", result.path("reason").asText());
    }

    @Test
    @DisplayName("A service failure returns null, preserving the listener's fail-closed policy")
    void serviceFailureShouldReturnNull() {
        when(client.safety(any())).thenThrow(
                new AiServiceClient.AiServiceException("ai_service_unavailable", true, 502, "down"));

        assertNull(classifier.classify("hi", SafetyClassifier.Surface.POST));
    }
}
