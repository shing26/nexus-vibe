package com.nexus.campus.task;

import com.nexus.campus.mapper.FunnelMapper;
import com.nexus.campus.metrics.RegistrationCohort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The funnel queries executed against the database the tests actually have.
 *
 * <p>{@code FunnelAggregateTaskTest} stubs the mapper, which is the right call for
 * arithmetic but leaves every SQL string in {@code FunnelMapper} unexecuted — the
 * exact gap that let {@code DATEADD('DAY', -7, CURRENT_TIMESTAMP)} reach a public
 * endpoint and fail only on MySQL. This runs the three reads on H2 so a syntax
 * error, a wrong column name, or a mapping that silently produces nulls shows up
 * here instead of in a ratio that looks merely low. The MySQL side of the same
 * three queries is the drill's {@code product-loop-drives-the-counters} step.</p>
 */
@SpringBootTest
@Sql({"/data.sql", "/test-users.sql"})
class FunnelAggregateIntegrationTest {

    @Autowired
    private FunnelMapper funnelMapper;

    @Test
    @DisplayName("The cohort read returns registrations with their first-post timestamps")
    void cohortReadMapsBothTimestamps() {
        List<RegistrationCohort> cohort = funnelMapper.selectRegistrationCohort(LocalDateTime.now().minusYears(1));

        assertThat(cohort).isNotEmpty();
        assertThat(cohort).allSatisfy(row -> {
            assertThat(row.getUserId()).isNotNull();
            assertThat(row.getRegisteredAt()).as("registered_at maps to the field").isNotNull();
        });
        // data.sql has a post whose author is also a seeded user, so at least one row
        // has to carry a first-post time. Without this the MIN() subquery could return
        // null for everybody and the ratio would read a permanent, plausible zero.
        assertThat(cohort).anySatisfy(row -> assertThat(row.getFirstPostAt()).isNotNull());
    }

    @Test
    @DisplayName("The union of post and comment authors counts each author once")
    void contentAuthorsAreDistinct() {
        long authors = funnelMapper.countContentAuthorsSince(LocalDateTime.now().minusYears(1));
        long users = funnelMapper.countUsers();

        assertThat(users).isPositive();
        assertThat(authors).isPositive().isLessThanOrEqualTo(users);
    }

    @Test
    @DisplayName("A real sweep leaves both gauges inside 0..1")
    void sweepPublishesUsableRatios() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FunnelAggregateTask task = new FunnelAggregateTask(funnelMapper, registry);
        task.registerGauges();

        task.aggregate();

        for (String name : List.of("funnel.activation.ratio", "funnel.active.content.d7.ratio")) {
            Gauge gauge = registry.get(name).gauge();
            assertThat(gauge).as(name).isNotNull();
            assertThat(gauge.value()).as(name).isBetween(0.0, 1.0);
        }
    }
}
