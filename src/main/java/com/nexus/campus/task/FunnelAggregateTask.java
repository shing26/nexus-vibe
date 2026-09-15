package com.nexus.campus.task;

import com.nexus.campus.mapper.FunnelMapper;
import com.nexus.campus.metrics.RegistrationCohort;
import com.nexus.campus.util.TraceIds;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns the two questions a community operator asks into gauges.
 *
 * <p>{@code funnel.activation.ratio} — of the accounts registered in the recent
 * cohort, how many published something within a week. {@code
 * funnel.active.content.d7.ratio} — how many of everybody who ever registered has
 * produced content in the last seven days. The seven operational meters already in
 * the repo say whether the machinery is up; these say whether anything it processed
 * mattered.</p>
 *
 * <p>Snapshot, not scrape-time query, for the same reason {@code
 * ai_review_pending_posts} is one: a Prometheus scrape arrives every fifteen seconds
 * and a gauge that answered with a {@code COUNT} over {@code vibe_post} would let the
 * monitoring stack load the database. The value is therefore stale by up to a day,
 * which is the right exchange for a number whose unit is a percentage point.</p>
 */
@Slf4j
@Component
public class FunnelAggregateTask {

    /** How long after registering a first post still counts as activation. */
    static final int ACTIVATION_LAG_DAYS = 7;

    private final FunnelMapper funnelMapper;
    private final MeterRegistry meterRegistry;

    /**
     * Last computed snapshot, read by the gauges. Held as a plain field with an
     * accessor rather than an {@code AtomicReference}, because that is how
     * {@code LlmClient} exposes its breaker gauge too and Micrometer wants a
     * {@code ToDoubleFunction} of the state object either way.
     */
    private volatile double activationRatio;
    private volatile double contentActiveRatio;

    @Value("${campus.metrics.funnel.window-days:30}")
    private int cohortWindowDays = 30;

    /**
     * Same switch that owns the cron clock. The job beans stay registered when it is
     * off — the gauges have to exist for the scrape contract — so the startup run has
     * to read it rather than answer to the scheduler.
     */
    @Value("${campus.scheduling.enabled:true}")
    private boolean schedulingEnabled = true;

    public FunnelAggregateTask(FunnelMapper funnelMapper, MeterRegistry meterRegistry) {
        this.funnelMapper = funnelMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("funnel.activation.ratio", this, FunnelAggregateTask::activationRatioValue)
                .description("Share of the recent registration cohort that published within "
                        + ACTIVATION_LAG_DAYS + " days")
                .register(meterRegistry);
        Gauge.builder("funnel.active.content.d7.ratio", this, FunnelAggregateTask::contentActiveRatioValue)
                .description("Share of all registered users with a post or comment in the last "
                        + ACTIVATION_LAG_DAYS + " days")
                .register(meterRegistry);
    }

    /**
     * Both ratios read 0 until the first sweep, and a dashboard cannot tell a zero
     * that means "nobody activated" apart from a zero that means "nobody has looked
     * yet". One pass at boot costs three indexed reads and removes the ambiguity.
     */
    @EventListener(ApplicationReadyEvent.class)
    void aggregateOnStartup() {
        if (!schedulingEnabled) {
            return;
        }
        aggregate();
    }

    /** Daily; the default is off-peak and offset from the other sweeps' minutes. */
    @Scheduled(cron = "${campus.metrics.funnel.cron:0 17 3 * * ?}")
    public void aggregate() {
        TraceIds.runAsJob("funnel-aggregate", this::aggregateOnce);
    }

    private void aggregateOnce() {
        try {
            LocalDateTime now = LocalDateTime.now();
            double activation = computeActivationRatio(now.minusDays(cohortWindowDays));
            double activeContent = computeContentActiveRatio(now.minusDays(ACTIVATION_LAG_DAYS));
            this.activationRatio = activation;
            this.contentActiveRatio = activeContent;
            log.info("[FUNNEL] activation={} cohort_window_days={} active_content_d7={} at {}",
                    format(activation), cohortWindowDays, format(activeContent), now);
        } catch (RuntimeException e) {
            // The previous snapshot stays published. A failed sweep is worth reading
            // in the log, but an operator should not lose the last good number because
            // the database was briefly unreachable, and this job has no retry budget
            // to spend.
            log.warn("[FUNNEL] aggregation failed, keeping the previous snapshot: {}", e.toString());
        }
    }

    private double computeActivationRatio(LocalDateTime cohortSince) {
        List<RegistrationCohort> cohort = funnelMapper.selectRegistrationCohort(cohortSince);
        if (cohort.isEmpty()) {
            return 0.0;
        }
        long activated = cohort.stream()
                .filter(row -> row.getFirstPostAt() != null)
                .filter(row -> row.getRegisteredAt() != null)
                // toDays() truncates toward zero, so a first post that predates its own
                // registration — a backfilled create_time, a row moved by hand — reads as a
                // gap of 0 days and would be counted as activated. It is a data problem, not
                // a conversion, so it is excluded and the two boundaries stay honest.
                .filter(row -> {
                    Duration gap = Duration.between(row.getRegisteredAt(), row.getFirstPostAt());
                    return !gap.isNegative() && gap.toDays() < ACTIVATION_LAG_DAYS;
                })
                .count();
        return (double) activated / cohort.size();
    }

    private double computeContentActiveRatio(LocalDateTime since) {
        long users = funnelMapper.countUsers();
        return clampToUnit("funnel.active.content.d7.ratio", funnelMapper.countContentAuthorsSince(since), users);
    }

    /** Read by the gauge registered for {@code funnel.activation.ratio}. */
    double activationRatioValue() {
        return activationRatio;
    }

    /** Read by the gauge registered for {@code funnel.active.content.d7.ratio}. */
    double contentActiveRatioValue() {
        return contentActiveRatio;
    }

    /**
     * Divide, and refuse to publish a percentage above 100.
     *
     * <p>A content author with no {@code sys_user} row behind them is possible —
     * {@code vibe_post.user_id} is not a foreign key, so an account removed by hand
     * leaves its posts counting — and the result is a numerator that outsteps the
     * denominator. The gauge still says what it says, but it says it as "everybody is
     * active" and puts the disagreement in the log, which is the only place anyone can
     * act on it.</p>
     */
    private static double clampToUnit(String name, long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        double ratio = (double) numerator / denominator;
        if (ratio > 1.0) {
            log.warn("[FUNNEL] {} computed as {}/{} = {}, clamped to 1.0: content exists whose author "
                            + "is no longer registered", name, numerator, denominator, format(ratio));
            return 1.0;
        }
        return ratio;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
