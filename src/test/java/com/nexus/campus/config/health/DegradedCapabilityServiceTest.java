package com.nexus.campus.config.health;

import com.nexus.campus.dto.CapabilityStatusVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the self-contained half of the read model: the closed vocabulary and what counts as
 * degraded.
 *
 * <p>No Spring context here, because the questions are pure mapping questions and a context would
 * add a database and two live probes to answer them. {@code CapabilityStatusIntegrationTest}
 * covers the same table through the real health group and the real filter chain.</p>
 */
class DegradedCapabilityServiceTest {

    private static final String ES_REASON = "Elasticsearch probe failed: Connection refused to http://127.0.0.1:9200";

    @Test
    @DisplayName("Everything up returns an empty list")
    void everythingUpIsSilent() {
        List<CapabilityStatusVo> degraded = shapeDegraded(Map.of(
                "db", Health.up().build(),
                "redis", Health.up().build(),
                "elasticsearch", Health.up().build(),
                "llm", Health.up().build()));

        assertThat(degraded).isEmpty();
    }

    @Test
    @DisplayName("A degraded Elasticsearch becomes the search capability, not the component name")
    void elasticsearchBecomesSearch() {
        List<CapabilityStatusVo> degraded = shapeDegraded(Map.of(
                "elasticsearch", DependencyHealth.degraded(ES_REASON)));

        assertThat(degraded).hasSize(1);
        assertThat(degraded.get(0).key()).isEqualTo("search");
        // B4's sentence, in the user's words; the component name must not be what a visitor reads.
        assertThat(degraded.get(0).impact()).containsIgnoringCase("search results may be incomplete");
        assertThat(degraded.get(0).impact()).doesNotContainIgnoringCase("elasticsearch");
    }

    @Test
    @DisplayName("A degraded LLM becomes the ai-review capability")
    void llmBecomesAiReview() {
        List<CapabilityStatusVo> degraded = shapeDegraded(Map.of(
                "llm", DependencyHealth.degraded("LLM probe failed or circuit breaker is open")));

        assertThat(degraded).hasSize(1);
        assertThat(degraded.get(0).key()).isEqualTo("ai-review");
        assertThat(degraded.get(0).impact()).containsIgnoringCase("review");
    }

    @Test
    @DisplayName("A degraded Redis becomes the cache capability")
    void redisBecomesCache() {
        List<CapabilityStatusVo> degraded = shapeDegraded(Map.of(
                "redis", DependencyHealth.degraded("Redis ping failed: connection refused")));

        assertThat(degraded).hasSize(1);
        assertThat(degraded.get(0).key()).isEqualTo("cache");
    }

    @Test
    @DisplayName("The database is never a capability, whatever it reports")
    void databaseIsNeverReported() {
        // Not an optional capability: the servable group is the database alone, so a database
        // incident is answered by /actuator/health, not by a "some features are limited" banner.
        assertThat(shapeDegraded(Map.of("db", DependencyHealth.degraded("connection refused")))).isEmpty();
    }

    @Test
    @DisplayName("A component outside the vocabulary cannot leak through by being degraded")
    void unknownComponentsAreIgnored() {
        assertThat(shapeDegraded(Map.of(
                "s3", DependencyHealth.degraded("bucket unreachable"),
                "db", DependencyHealth.degraded("connection refused")))).isEmpty();
    }

    @Test
    @DisplayName("DOWN is not DEGRADED; the aggregate document owns that incident")
    void downIsNotReported() {
        assertThat(shapeDegraded(Map.of("elasticsearch", Health.down().build()))).isEmpty();
    }

    @Test
    @DisplayName("Every reported key comes from the closed vocabulary, in declaration order")
    void keysAreClosedAndOrdered() {
        List<CapabilityStatusVo> degraded = shapeDegraded(Map.of(
                "redis", DependencyHealth.degraded("ping failed"),
                "llm", DependencyHealth.degraded("probe failed"),
                "elasticsearch", DependencyHealth.degraded(ES_REASON),
                "db", Health.up().build()));

        assertThat(degraded).extracting(CapabilityStatusVo::key)
                .containsExactly("search", "ai-review", "cache");
    }

    @Test
    @DisplayName("A missing deps group is silence, not an invented entry")
    void missingGroupReturnsNothing() {
        DegradedCapabilityService withNonComposite = serviceReturningGroup(Health.up().build());

        assertThat(withNonComposite.current()).isEmpty();
    }

    @Test
    @DisplayName("A null component map is silence")
    void nullComponentsAreSilent() {
        assertThat(serviceWith(Map.of()).toDegradedCapabilities(null)).isEmpty();
    }

    private static List<CapabilityStatusVo> shapeDegraded(Map<String, HealthComponent> components) {
        return serviceWith(components).toDegradedCapabilities(components);
    }

    private static DegradedCapabilityService serviceWith(Map<String, HealthComponent> components) {
        CompositeHealth composite = mock(CompositeHealth.class);
        when(composite.getComponents()).thenReturn(components);
        return serviceReturningGroup(composite);
    }

    private static DegradedCapabilityService serviceReturningGroup(HealthComponent group) {
        org.springframework.boot.actuate.health.HealthEndpoint endpoint =
                mock(org.springframework.boot.actuate.health.HealthEndpoint.class);
        when(endpoint.healthForPath("deps")).thenReturn(group);
        return new DegradedCapabilityService(endpoint);
    }
}
