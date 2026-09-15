package com.nexus.campus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The Overview dashboard's latency panel is only as real as one YAML key.
 *
 * <p>Spring Boot emits count/sum/max for the auto-configured {@code http.server.requests} timer and
 * no buckets unless {@code percentiles-histogram} is enabled, and {@code histogram_quantile} over a
 * non-existent {@code _bucket} series returns nothing. The panel therefore rendered "No data" on a
 * stack whose provisioning, scrape and alert rules were all verified green: every automated check
 * looked at the metrics endpoint or the rules file, and nothing had looked at a panel. This test is
 * what keeps the fix from being deleted by the next person who is trimming "unused" config.</p>
 *
 * <p>Read off the shipped YAML rather than a running context, matching
 * {@link ActuatorExposureContractTest}: the question is whether the file says it, and booting a
 * context to ask would not catch the file being wrong in the prod profile anyway.</p>
 */
class HttpMetricsHistogramContractTest {

    @Test
    @DisplayName("http.server.requests publishes histogram buckets, or the latency panel is blank")
    void httpRequestTimerPublishesBuckets() {
        Object enabled = distribution("percentiles-histogram");

        assertThat(enabled)
                .as("management.metrics.distribution.percentiles-histogram[http.server.requests]")
                .isNotNull()
                .satisfies(v -> assertThat(String.valueOf(v)).isEqualTo("true"));
    }

    /**
     * Bucket count is a scrape-size decision: Boot's default range is ~70 buckets per timer, and
     * these are multiplied by uri x method x status. Explicit SLO boundaries are what keep the panel
     * affordable, so an enabled histogram with no boundaries is not the shipped shape.
     */
    @Test
    @DisplayName("the histogram is bounded by explicit SLO buckets rather than the default range")
    void bucketBoundariesAreExplicit() {
        Object slo = distribution("slo");

        assertThat(slo)
                .as("management.metrics.distribution.slo[http.server.requests]")
                .isNotNull();
        assertThat(String.valueOf(slo))
                .contains("50ms", "1s", "30s");
    }

    @SuppressWarnings("unchecked")
    private Object distribution(String knob) {
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            for (Object document : new Yaml().loadAll(in)) {
                if (!(document instanceof Map)) {
                    continue;
                }
                Map<String, Object> root = (Map<String, Object>) document;
                Map<String, Object> distribution = asMap(asMap(
                        asMap(asMap(root.get("management")).get("metrics")).get("distribution")).get(knob));
                Object value = distribution.get("[http.server.requests]");
                if (value == null) {
                    // Boot's relaxed binding also accepts the unbracketed dotted key.
                    value = distribution.get("http.server.requests");
                }
                if (value != null) {
                    return value;
                }
            }
            return fail("no management.metrics.distribution." + knob + " in application.yml");
        } catch (Exception e) {
            throw new IllegalStateException("could not read application.yml", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object node) {
        return node instanceof Map ? (Map<String, Object>) node : Map.of();
    }
}
