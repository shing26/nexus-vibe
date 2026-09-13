package com.nexus.campus.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The single switch for cron.
 *
 * <p>Scheduling used to be enabled on the boot application class and on
 * {@code WebMvcConfig} at the same time, which meant every {@code @SpringBootTest}
 * started the real cron clock against the same context the tests were asserting
 * on. {@code AiReviewReconcileTask} fires on wall-clock minutes, and the verdict
 * it writes into {@code LlmHealthCache} then survives for five of them, so a job
 * waking up between two test methods could fail every later post closed and turn
 * an admin pin into a 404 — green on one run, red on the next, with the same
 * code. One property now decides who owns the clock.</p>
 *
 * <p>The job beans stay registered when this is off: {@code AiReviewReconcileTask}
 * publishes its backlog gauge in {@code @PostConstruct}, and tests still call the
 * sweep methods directly. Only the scheduler is switched.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "campus.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
