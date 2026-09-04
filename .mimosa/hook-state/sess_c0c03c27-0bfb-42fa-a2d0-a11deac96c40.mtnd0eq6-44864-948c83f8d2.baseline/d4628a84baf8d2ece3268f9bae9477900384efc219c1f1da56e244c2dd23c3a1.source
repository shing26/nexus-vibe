package com.nexus.campus.task;

import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.agent.LlmClient;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.mapper.VibePostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reconciliation task for the AI agent pipeline.
 *
 * <p>Every 5 minutes, re-publishes agent events for work the pipeline lost or
 * failed on: reviews stuck in REVIEWING (e.g. an app restart mid-review),
 * reviews in FAILED state (LLM outage or invalid output), and safety checks
 * that failed closed to PENDING_REVIEW with a "pending-llm" marker. Each cycle
 * first confirms the LLM is healthy (cached for 5 minutes) so outages don't
 * spin empty retries.</p>
 */
@Slf4j
@Component
public class AiReviewReconcileTask {

    private static final int BATCH_LIMIT = 10;
    private static final long HEALTH_CACHE_MILLIS = 5 * 60 * 1000;

    @Value("${campus.ai.reconcile.stale-minutes:10}")
    private long staleMinutes;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private LlmClient llmClient;

    @Value("${campus.ai.review.enabled:true}")
    private boolean reviewEnabled;

    @Value("${campus.ai.safety.enabled:true}")
    private boolean safetyEnabled;

    private volatile long lastHealthCheckAt;
    private volatile boolean lastHealthy;

    /**
     * Every 5 minutes, sweep stale AI review/safety states and re-trigger them.
     */
    @Scheduled(cron = "0 3/5 * * * ?")
    public void reconcile() {
        if (!reviewEnabled && !safetyEnabled) {
            return;
        }
        if (!isLlmHealthy()) {
            log.debug("[AI-RECONCILE] LLM unhealthy, skipping cycle.");
            return;
        }

        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(staleMinutes);

        if (reviewEnabled) {
            int retriggered = 0;
            retriggered += retriggerStaleReviews(AiReviewStatus.REVIEWING, staleBefore);
            retriggered += retriggerStaleReviews(AiReviewStatus.FAILED, staleBefore);
            if (retriggered > 0) {
                log.info("[AI-RECONCILE] Re-triggered {} stale reviews", retriggered);
            }
        }

        if (safetyEnabled) {
            List<VibePost> pendingSafety = vibePostMapper.selectPostsPendingSafetyRecheck(staleBefore, BATCH_LIMIT);
            for (VibePost post : pendingSafety) {
                log.info("[AI-RECONCILE] Re-running safety check for post {}", post.getId());
                eventPublisher.publishEvent(new AiSafetyCheckEvent(this, post.getId(), post.getTitle(), post.getContent(), post.getUserId()));
            }
        }
    }

    private int retriggerStaleReviews(AiReviewStatus status, LocalDateTime staleBefore) {
        List<VibePost> stalePosts = vibePostMapper.selectStaleAiReviewPosts(
                status.getCode(), staleBefore, BATCH_LIMIT);
        for (VibePost post : stalePosts) {
            log.info("[AI-RECONCILE] Re-triggering {} review for post {}", status, post.getId());
            eventPublisher.publishEvent(new AiReviewEvent(this, post.getId(), post.getTitle(), post.getContent(), post.getUserId(), true));
        }
        return stalePosts.size();
    }

    /**
     * Cached LLM health probe: at most one real call per cycle (shared by the
     * review and safety sweeps). The probe itself retries like any LLM call
     * and fails fast while the breaker is open.
     */
    private boolean isLlmHealthy() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckAt > HEALTH_CACHE_MILLIS) {
            lastHealthy = llmClient.isHealthy();
            lastHealthCheckAt = now;
        }
        return lastHealthy;
    }
}
