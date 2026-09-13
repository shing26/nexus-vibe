package com.nexus.campus.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The monitoring stack only works if the app is scrapeable, so this is the contract Prometheus
 * relies on. Nothing in the repo reached an actuator endpoint before, which is how "metrics are
 * not exposed" stayed invisible for as long as it did.
 *
 * <p>Assertions are deliberately limited to {@code jvm_*} and {@code http_server_requests_*}.
 * OS and disk metrics come from {@code SystemMetricsAutoConfiguration}, and the {@code ci-linux}
 * profile runs surefire with {@code -XX:-UseContainerSupport} precisely because container cgroup
 * detection used to crash the JVM on the runner; asserting those names here would be asserting
 * the disabled configuration. The prod container check lives in the observability drill.</p>
 *
 * <p>{@link AutoConfigureObservability} is not decoration: Boot's test context customizer sets
 * {@code management.defaults.metrics.export.enabled=false} for every {@code @SpringBootTest}, which
 * silently unregisters the scrape endpoint. Without the opt-in this test reads a 404 on
 * {@code /actuator/prometheus} while the application configuration is perfectly correct.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(metrics = true)
class ActuatorMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/prometheus renders JVM and HTTP request metrics")
    void prometheusScrapeReturnsMetrics() throws Exception {
        // Any filtered request creates the http_server_requests series, so scrape after one.
        // /actuator/info is used rather than /actuator/health so this test does not depend on
        // dependency health at all; HealthSemanticsIntegrationTest owns that contract.
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("# TYPE jvm_memory_used_bytes gauge")))
                .andExpect(content().string(containsString("http_server_requests_seconds_count")));
    }

    @Test
    @DisplayName("exposure stays an allowlist: prometheus is in, the metrics API is out")
    void onlyAdvertisedEndpointsAreExposed() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
