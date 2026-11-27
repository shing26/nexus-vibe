package com.nexus.campus.ai.health;

import com.nexus.campus.ai.config.AiServiceProperties;
import com.nexus.campus.ai.model.ModelGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderHealthProbeTest {

    @Test
    void aFailedProbeIsCachedForTheWindow() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.getLlm().setHealthCacheSeconds(60);
        CountingGateway gateway = new CountingGateway(false);
        ProviderHealthProbe probe = new ProviderHealthProbe(gateway, properties);

        assertFalse(probe.isHealthy());
        gateway.healthy = true;
        assertFalse(probe.isHealthy(), "the cached verdict must not flip before the window");
        assertEquals(1, gateway.calls);
    }

    @Test
    void aZeroWindowObservesRecovery() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.getLlm().setHealthCacheSeconds(0);
        CountingGateway gateway = new CountingGateway(false);
        ProviderHealthProbe probe = new ProviderHealthProbe(gateway, properties);

        assertFalse(probe.isHealthy());
        gateway.healthy = true;
        assertTrue(probe.isHealthy());
        assertEquals(2, gateway.calls);
    }

    private static final class CountingGateway implements ModelGateway {

        private boolean healthy;
        private int calls;

        private CountingGateway(boolean healthy) {
            this.healthy = healthy;
        }

        @Override
        public ModelReply complete(String system, String user, double temperature,
                                   com.nexus.campus.ai.model.OutputSchema schema) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isHealthy() {
            calls++;
            return healthy;
        }
    }
}
