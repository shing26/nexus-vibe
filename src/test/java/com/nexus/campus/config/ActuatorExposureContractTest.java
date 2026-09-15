package com.nexus.campus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * What each deployment stage is willing to answer on its own port.
 *
 * <p>This is read straight off the shipped YAML rather than from a running context on purpose: the
 * prod profile needs a database, and booting it in a test would either fake that dependency or
 * make the test slow enough that nobody runs it. The question here is narrow and static -- does
 * prod's allowlist contain anything it should not -- and a file assertion answers it exactly.</p>
 */
class ActuatorExposureContractTest {

    /**
     * Prod is the shipped surface: scrapeable, but no metrics browsing and no config reading.
     * The public edge (docker/nginx/nginx.conf) then narrows this to one health URL.
     */
    @Test
    @DisplayName("prod exposes health, info and prometheus only")
    void prodAllowlistIsExact() {
        assertThat(exposureOf("application-prod.yml")).containsExactlyInAnyOrder("health", "info", "prometheus");
    }

    /**
     * Dev keeps {@code /actuator/metrics}: it is how a counter name is checked before an alert is
     * written for it. This exists because the previous round widened the base allowlist to prod's
     * shape and took that tool away from every profile without meaning to.
     */
    @Test
    @DisplayName("the dev/base surface keeps the metrics browsing endpoint")
    void baseSurfaceKeepsMetrics() {
        assertThat(exposureOf("application.yml")).contains("metrics", "prometheus", "health");
    }

    @SuppressWarnings("unchecked")
    private List<String> exposureOf(String file) {
        try (InputStream in = new ClassPathResource(file).getInputStream()) {
            for (Object document : new Yaml().loadAll(in)) {
                if (!(document instanceof Map)) {
                    continue;
                }
                Map<String, Object> root = (Map<String, Object>) document;
                Map<String, Object> management = asMap(root.get("management"));
                Map<String, Object> endpoints = asMap(management.get("endpoints"));
                Map<String, Object> web = asMap(endpoints.get("web"));
                Map<String, Object> exposure = asMap(web.get("exposure"));
                Object include = exposure.get("include");
                if (include != null) {
                    List<String> names = new ArrayList<>();
                    for (String part : String.valueOf(include).split(",")) {
                        names.add(part.trim());
                    }
                    return names;
                }
            }
            return fail("no management.endpoints.web.exposure.include in " + file);
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object node) {
        return node instanceof Map ? (Map<String, Object>) node : Map.of();
    }
}
