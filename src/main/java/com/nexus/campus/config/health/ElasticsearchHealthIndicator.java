package com.nexus.campus.config.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Elasticsearch health, live.
 *
 * <p>Search already degrades to MySQL when ES is missing, so the outage is survivable and belongs in
 * DEGRADED. The probe is a real request rather than the availability flag {@code PostSearchService}
 * captures at startup, because that flag cannot see an index cluster that comes back after boot or
 * one that dies afterwards, and a health endpoint that reports the state of the JVM's constructor is
 * not a health endpoint.</p>
 */
@Component
public class ElasticsearchHealthIndicator implements HealthIndicator {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient;
    private final String esBase;

    public ElasticsearchHealthIndicator(@Value("${campus.es.uri:http://localhost:9200}") String esBase) {
        this.esBase = esBase;
        this.httpClient = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
    }

    @Override
    public Health health() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/"))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Health.up().build();
            }
            return DependencyHealth.degraded("Elasticsearch answered HTTP " + response.statusCode());
        } catch (Exception e) {
            return DependencyHealth.degraded("Elasticsearch probe failed: " + e.getMessage());
        }
    }
}
