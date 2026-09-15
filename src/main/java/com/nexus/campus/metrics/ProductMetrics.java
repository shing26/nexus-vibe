package com.nexus.campus.metrics;

import com.nexus.campus.enums.PostStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The four events the product exists to produce: somebody joined, somebody published,
 * somebody approved or rejected a post, somebody replied.
 *
 * <p>The seven operational meters already in the repo answer "is the machinery up".
 * None of them answer "did the thing we built get used", which is the question a
 * dashboard is for. The counters live behind this one class rather than at each
 * {@code Counter.builder} call site for two reasons: the metric names then have a
 * single owner, testable against a {@code SimpleMeterRegistry}, and a service that
 * wants to record a fact depends on a component it can be handed a mock of. The
 * alternative was four services each spelling out the same name, with unit tests
 * that could only be written against the static builder.</p>
 *
 * <p>Labels are closed sets derived from enums, never free text or ids — a counter
 * tagged with a post id is a memory leak that reports to Prometheus.</p>
 *
 * <p>Meter names are not the same strings the scrape shows. The Prometheus naming convention
 * reserves the {@code _created} suffix (client_golang publishes a counter's start timestamp as
 * {@code <name>_created}), so it strips a trailing {@code created} token from the meter name
 * before appending {@code _total}: a counter named {@code post.created} exports as
 * {@code post_total}, and {@code comment.created} as {@code comment_total}. Those two names were
 * what the drill, the panel and the README all asked for, which is how the first real scrape
 * noticed. Hence {@code *.submitted}. {@code ProductMetricsPrometheusNamesTest} renders the four
 * through a real {@code PrometheusMeterRegistry} because a {@code SimpleMeterRegistry} keeps the
 * name verbatim and cannot see this at all.</p>
 */
@Component
public class ProductMetrics {

    /** Lifecycle statuses a post can be created into; anything else is recorded as {@code other}. */
    private static final Map<Integer, String> POST_CREATED_LABELS = Map.of(
            PostStatus.ACTIVE.getCode(), "published",
            PostStatus.PENDING_REVIEW.getCode(), "pending-review",
            PostStatus.REJECTED.getCode(), "rejected");

    private final MeterRegistry meterRegistry;

    public ProductMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRegistration() {
        counter("user.registered", null, null).increment();
    }

    public void recordPostCreated(Integer status) {
        // The null test is not paranoia: Map.of() returns a map that refuses null even
        // as a probe, so getOrDefault(null, ...) throws. A metering line that can throw
        // is one that can fail a request which had already succeeded.
        String label = status == null ? "other" : POST_CREATED_LABELS.getOrDefault(status, "other");
        counter("post.submitted", "status", label).increment();
    }

    /**
     * @param action {@code approve} or {@code reject} — the admin's decision, not the
     *               post's resulting status, because the funnel asks how many
     *               submissions left the queue by a human.
     */
    public void recordPostAudited(String action) {
        counter("post.audited", "action", action).increment();
    }

    public void recordCommentCreated() {
        counter("comment.submitted", null, null).increment();
    }

    private Counter counter(String name, String tag, String value) {
        Counter.Builder builder = Counter.builder(name).description("Product-loop event: " + name);
        if (tag != null) {
            builder = builder.tag(tag, value);
        }
        // Registering an id that already exists returns the existing meter, which is
        // what makes this cheap enough to call on every request.
        return builder.register(meterRegistry);
    }
}
