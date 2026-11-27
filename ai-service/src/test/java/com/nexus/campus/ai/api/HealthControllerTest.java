package com.nexus.campus.ai.api;

import com.nexus.campus.ai.health.ProviderHealthProbe;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final ProviderHealthProbe probe = mock(ProviderHealthProbe.class);
    private final HealthController controller = new HealthController(probe);

    @Test
    void readyzIs200WhenTheProviderProbePasses() {
        when(probe.isHealthy()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.readyz();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ready", response.getBody().get("status"));
    }

    @Test
    void readyzIs503WhenTheProviderProbeFails() {
        when(probe.isHealthy()).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.readyz();

        assertEquals(503, response.getStatusCode().value());
        assertEquals("not_ready", response.getBody().get("status"));
    }

    @Test
    void healthzNeverCallsTheDependency() {
        controller.healthz();

        org.mockito.Mockito.verifyNoInteractions(probe);
    }
}
