package com.nexus.campus.config;

import com.nexus.campus.NexusCampusApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test context must not own a cron clock.
 *
 * <p>This is the regression test for why CI turned red on a commit that changed
 * one markdown file: {@code AiReviewReconcileTask} fires on wall-clock minutes,
 * its probe result is cached for five minutes in {@code LlmHealthCache}, and a
 * sweep landing between two test methods used to read Mockito's default
 * {@code false} from the just-reset {@code @MockBean}. That verdict then failed
 * every later post closed, which surfaced as an unrelated pin assertion getting
 * a 404. Switching scheduling off removes the whole class of interference rather
 * than the one symptom.</p>
 */
@SpringBootTest(classes = NexusCampusApplication.class)
class SchedulingGateTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("No scheduler is registered in the test context")
    void cronClockIsNotRunningInTests() {
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }

    @Test
    @DisplayName("An inbound X-Trace-Id is not trusted, whatever the proxy-trust flag says")
    void traceTrustIsItsOwnSwitch() {
        // The security flag is on in production because nginx fronts the app; reusing it for
        // trace ids meant a public deployment accepted client-chosen ids by accident.
        assertThat(context.getEnvironment()
                .getProperty("campus.trace.trust-inbound-header", Boolean.class))
                .isFalse();
    }
}
