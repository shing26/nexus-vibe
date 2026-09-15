package com.nexus.campus.task;

import com.nexus.campus.mapper.FunnelMapper;
import com.nexus.campus.metrics.RegistrationCohort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The arithmetic of the two ratios, with the database reduced to a stub.
 *
 * <p>Three things are worth pinning and none of them is "the query returns rows".
 * An empty population must publish {@code 0} rather than NaN or nothing at all,
 * because a Prometheus gauge that is absent reads as "no data" in Grafana and a
 * panel that says no data is a panel nobody looks at. A first post six days and
 * twenty-three hours after registration counts and one seven days later does not,
 * which is the boundary the metric's name promises. And a failure keeps the previous
 * snapshot rather than overwriting it with a zero nobody asked for.</p>
 */
class FunnelAggregateTaskTest {

    private FunnelMapper mapper;
    private SimpleMeterRegistry registry;
    private FunnelAggregateTask task;

    @BeforeEach
    void setUp() {
        mapper = mock(FunnelMapper.class);
        registry = new SimpleMeterRegistry();
        task = new FunnelAggregateTask(mapper, registry);
        task.registerGauges();
    }

    private double gauge(String name) {
        Gauge found = registry.get(name).gauge();
        assertThat(found).as("%s registered", name).isNotNull();
        return found.value();
    }

    private static RegistrationCohort cohort(long id, LocalDateTime registeredAt, LocalDateTime firstPostAt) {
        RegistrationCohort row = new RegistrationCohort();
        row.setUserId(id);
        row.setRegisteredAt(registeredAt);
        row.setFirstPostAt(firstPostAt);
        return row;
    }

    @Test
    @DisplayName("An empty database publishes two zeroes, not NaN and not an absent gauge")
    void emptyDatabaseReadsZero() {
        when(mapper.selectRegistrationCohort(any())).thenReturn(List.of());
        when(mapper.countUsers()).thenReturn(0L);

        task.aggregate();

        assertThat(gauge("funnel.activation.ratio")).isZero();
        assertThat(gauge("funnel.active.content.d7.ratio")).isZero();
    }

    @Test
    @DisplayName("Activation cuts at seven days and divides by the cohort, not by all users")
    void activationUsesTheSevenDayBoundary() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.selectRegistrationCohort(any())).thenReturn(List.of(
                cohort(1, now.minusDays(10), now.minusDays(9).minusHours(1)),   // 23h later: activated
                cohort(2, now.minusDays(8), now.minusDays(1).minusHours(1)),    // 6d23h later: activated
                cohort(3, now.minusDays(13), now.minusDays(5)),                 // 8d later: past the lag
                cohort(4, now.minusDays(2), null),                              // never posted
                cohort(5, now.minusDays(5), now.minusDays(6))));                // posted before registering
        when(mapper.countUsers()).thenReturn(40L);
        when(mapper.countContentAuthorsSince(any())).thenReturn(10L);

        task.aggregate();

        // 2 of 5 in the cohort, 10 of 40 overall. The denominators differ on purpose: one
        // asks what a new intake did, the other what the whole base is still doing.
        assertThat(gauge("funnel.activation.ratio")).isCloseTo(0.4, within(0.0001));
        assertThat(gauge("funnel.active.content.d7.ratio")).isCloseTo(0.25, within(0.0001));
    }

    @Test
    @DisplayName("A failed sweep keeps the last good snapshot instead of writing zero")
    void aggregationFailureKeepsPreviousValue() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.selectRegistrationCohort(any())).thenReturn(List.of(
                cohort(1, now.minusDays(3), now.minusDays(2))));
        when(mapper.countUsers()).thenReturn(1L);
        when(mapper.countContentAuthorsSince(any())).thenReturn(1L);
        task.aggregate();
        assertThat(gauge("funnel.activation.ratio")).isEqualTo(1.0);

        when(mapper.selectRegistrationCohort(any())).thenThrow(new RuntimeException("connection refused"));
        task.aggregate();

        assertThat(gauge("funnel.activation.ratio")).as("still the last good number").isEqualTo(1.0);
    }

    @Test
    @DisplayName("A numerator that outsteps its denominator cannot publish 150% active")
    void ratioIsClampedWhenTheCountsDisagree() {
        // A user removed by hand leaves their content counting with nobody behind it:
        // vibe_post.user_id is not a foreign key. The gauge then has to say either "all
        // of them" or a number a dashboard cannot plot, so it says "all of them" and the
        // disagreement goes to the log, where it is a data problem rather than a mystery.
        when(mapper.selectRegistrationCohort(any())).thenReturn(List.of());
        when(mapper.countUsers()).thenReturn(2L);
        when(mapper.countContentAuthorsSince(any())).thenReturn(3L);

        task.aggregate();

        assertThat(gauge("funnel.active.content.d7.ratio")).isLessThanOrEqualTo(1.0);
        assertThat(gauge("funnel.active.content.d7.ratio")).isPositive();
    }
}
