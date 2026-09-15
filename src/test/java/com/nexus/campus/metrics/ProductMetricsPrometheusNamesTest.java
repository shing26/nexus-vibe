package com.nexus.campus.metrics;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The names as the internet actually sees them.
 *
 * <p>{@link ProductMetricsTest} asserts against a {@code SimpleMeterRegistry}, which keeps a
 * meter's name verbatim. The deployed scrape is produced by {@code PrometheusMeterRegistry},
 * whose naming convention reserves the {@code _created} suffix and strips a trailing
 * {@code created} token, so {@code post.created} left the process as {@code post_total} while the
 * drill, the Grafana panel and the README were all asking for {@code post_created_total}. Four
 * green unit tests and one absent metric line: the assertion that mattered lived in a dialect
 * nothing was checking. This test is that missing check, and the reason the names are
 * {@code *.submitted}.</p>
 */
class ProductMetricsPrometheusNamesTest {

    /** A sample line is "<name>{<labels>} <value>"; HELP and TYPE lines start with "#". */
    private static final Pattern SAMPLE = Pattern.compile("^(\\S+?)(\\{[^}]*\\})?\\s+([0-9eE.+-]+)$");

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final ProductMetrics metrics = new ProductMetrics(registry);

    private List<String> sampleLines() {
        return registry.scrape().lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private List<String> exportedNames() {
        return sampleLines().stream()
                .map(line -> SAMPLE.matcher(line))
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .toList();
    }

    @Test
    @DisplayName("the four product events export under the names the panel and the drill query")
    void exportedNamesSurviveThePrometheusConvention() {
        metrics.recordRegistration();
        metrics.recordPostCreated(1);
        metrics.recordPostCreated(2);
        metrics.recordPostAudited("approve");
        metrics.recordCommentCreated();

        assertThat(exportedNames()).contains(
                "user_registered_total",
                "post_submitted_total",
                "post_audited_total",
                "comment_submitted_total");

        // The names the reserved suffix used to produce. If one of these comes back, somebody
        // renamed a meter onto `*.created` and the scrape silently lost the second word.
        assertThat(exportedNames()).doesNotContain("post_total", "comment_total");
    }

    @Test
    @DisplayName("the closed label sets reach the scrape as labels, not as separate meters")
    void labelsReachTheScrape() {
        metrics.recordPostCreated(1);
        metrics.recordPostAudited("reject");

        assertThat(sampleLines())
                .anyMatch(line -> line.startsWith("post_submitted_total{")
                        && line.contains("status=\"published\""));
        assertThat(sampleLines())
                .anyMatch(line -> line.startsWith("post_audited_total{")
                        && line.contains("action=\"reject\""));
    }
}
