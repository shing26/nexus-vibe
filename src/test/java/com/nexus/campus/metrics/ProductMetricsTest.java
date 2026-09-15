package com.nexus.campus.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The names and labels a dashboard selects on, asserted against a real registry
 * rather than a mock. A mock proves the method was called; it cannot notice that a
 * counter was renamed, that a label value arrived as an enum's {@code name()} where
 * the panel expects a lowercase literal, or that two different events were quietly
 * given the same meter.
 */
class ProductMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ProductMetrics metrics = new ProductMetrics(registry);

    private double count(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).counter().count();
    }

    @Test
    @DisplayName("user_registered_total counts one registration")
    void registration() {
        metrics.recordRegistration();
        metrics.recordRegistration();

        assertThat(registry.get("user.registered").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("post_submitted splits on a lifecycle label, never a status integer")
    void postCreatedCarriesTheStatusLabel() {
        metrics.recordPostCreated(1);
        metrics.recordPostCreated(1);
        metrics.recordPostCreated(2);
        metrics.recordPostCreated(3);
        metrics.recordPostCreated(null);

        assertThat(count("post.submitted", "status", "published")).isEqualTo(2.0);
        assertThat(count("post.submitted", "status", "pending-review")).isEqualTo(1.0);
        assertThat(count("post.submitted", "status", "rejected")).isEqualTo(1.0);
        // A status the enum does not know about lands in `other` instead of throwing
        // on a request path that had already written its row.
        assertThat(count("post.submitted", "status", "other")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("post_audited separates the two decisions an admin makes")
    void auditedCarriesTheAction() {
        metrics.recordPostAudited("approve");
        metrics.recordPostAudited("reject");
        metrics.recordPostAudited("reject");

        assertThat(count("post.audited", "action", "approve")).isEqualTo(1.0);
        assertThat(count("post.audited", "action", "reject")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("comment_submitted_total is its own meter, not a tag on post_submitted")
    void commentCreated() {
        metrics.recordCommentCreated();

        assertThat(registry.get("comment.submitted").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("post.submitted").counter()).isNull();
    }
}
