package com.nexus.campus.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.SysMessageService;
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
 * that failed closed to PENDING_REVIEW with a "pending-llm" marker. It also
 * retires reviews whose attempt budget was exhausted by a worker that died
 * mid-attempt (the lease claim refuses those forever). Each cycle first
 * confirms the LLM is healthy (cached, shared {@link LlmHealthCache}) so
 * outages don't spin empty retries.</p>
 */
@Slf4j
@Component
public class AiReviewReconcileTask {

    private static final int BATCH_LIMIT = 10;

    @Value("${campus.ai.reconcile.stale-minutes:10}")
    private long staleMinutes;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private LlmHealthCache llmHealthCache;

    @Autowired
    private SysMessageService sysMessageService;

    @Value("${campus.ai.review.enabled:true}")
    private boolean reviewEnabled;

    @Value("${campus.ai.review.max-attempts:5}")
    private int maxAttempts;

    @Value("${campus.ai.safety.enabled:true}")
    private boolean safetyEnabled;

    /**
     * Every 5 minutes, sweep stale AI review/safety states and re-trigger them.
     */
    @Scheduled(cron = "0 3/5 * * * ?")
    public void reconcile() {
        if (!reviewEnabled && !safetyEnabled) {
            return;
        }
        // Budget-exhausted sweep runs regardless of LLM health: those posts
        // are unclaimable dead state that no retry path will ever touch.
        if (reviewEnabled) {
            sweepBudgetExhaustedReviews();
        }
        if (!llmHealthCache.isHealthy()) {
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

    /**
     * Retires posts stuck in REVIEWING with a spent attempt budget and an
     * expired lease: no claim can ever win them again, so they transition to
     * FAILED once (the conditional update also arbitrates concurrent task
     * instances — only the one that flips the row notifies the author).
     */
    private void sweepBudgetExhaustedReviews() {
        List<VibePost> stuck = vibePostMapper.selectReviewingBudgetExhausted(maxAttempts, BATCH_LIMIT);
        for (VibePost post : stuck) {
            int updated = vibePostMapper.update(null, new LambdaUpdateWrapper<VibePost>()
                    .eq(VibePost::getId, post.getId())
                    .eq(VibePost::getAiReviewed, AiReviewStatus.REVIEWING.getCode())
                    .set(VibePost::getAiReviewed, AiReviewStatus.FAILED.getCode()));
            if (updated > 0) {
                log.info("[AI-RECONCILE] Post {} REVIEWING with exhausted budget, retired to FAILED", post.getId());
                notifyBudgetExhausted(post);
            }
        }
    }

    private void notifyBudgetExhausted(VibePost post) {
        if (post.getUserId() == null) {
            return;
        }
        try {
            sysMessageService.sendMessage(SysMessage.FROM_SYSTEM, post.getUserId(),
                    "你的帖子《" + post.getTitle() + "》的 AI 评审连续 " + maxAttempts
                            + " 次失败，已停止自动重试。内容本身不受影响；如需重新评审，请联系管理员。",
                    SysMessage.TYPE_SYSTEM);
        } catch (Exception e) {
            log.warn("Failed to notify author {} about exhausted review budget on post {}: {}",
                    post.getUserId(), post.getId(), e.getMessage());
        }
    }

    private int retriggerStaleReviews(AiReviewStatus status, LocalDateTime staleBefore) {
        List<VibePost> stalePosts = vibePostMapper.selectStaleAiReviewPosts(
                status.getCode(), staleBefore, maxAttempts, BATCH_LIMIT);
        for (VibePost post : stalePosts) {
            log.info("[AI-RECONCILE] Re-triggering {} review for post {}", status, post.getId());
            eventPublisher.publishEvent(new AiReviewEvent(this, post.getId(), post.getTitle(), post.getContent(), post.getUserId(), true));
        }
        return stalePosts.size();
    }

}
