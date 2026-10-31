package com.nexus.campus.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.campus.agent.AgentMentionEvent;
import com.nexus.campus.agent.AiCommentSafetyCheckEvent;
import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.enums.CommentStatus;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.SysMessageService;
import com.nexus.campus.service.impl.AgentReplyComments;
import com.nexus.campus.util.TraceIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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

    @Autowired
    private CampusAiProperties aiProperties;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private VibeCommentMapper vibeCommentMapper;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private LlmHealthCache llmHealthCache;

    @Autowired
    private SysMessageService sysMessageService;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Last observed review backlog, exposed as {@code ai_review_pending_posts}. A snapshot rather
     * than a scrape-time query: Prometheus scrapes every 15s, and an endpoint that answered a
     * scrape with a COUNT over vibe_post would let the monitoring stack load the database.
     */
    private final AtomicLong reviewBacklog = new AtomicLong();

    /**
     * Posts the safety fail-closed path is holding. Refreshed on the same schedule as the review
     * backlog and, like it, before the health gate below — a fail-closed backlog only exists while
     * something is wrong, so it is least useful exactly when a gate would skip the update.
     */
    private final AtomicLong safetyBacklog = new AtomicLong();

    /**
     * Comments the comment-safety path is holding on a pending-llm marker. Same
     * refresh rule as the post backlogs: refreshed before the health gate.
     */
    private final AtomicLong commentSafetyBacklog = new AtomicLong();

    /**
     * Placeholder replies waiting for their real answer (ADR-0018). Same
     * refresh rule as the other backlogs: refreshed before the health gate.
     */
    private final AtomicLong mentionReplyBacklog = new AtomicLong();

    @Autowired
    private AgentReplyComments agentReplyComments;

    @PostConstruct
    void registerBacklogGauge() {
        Gauge.builder("ai.review.pending.posts", reviewBacklog, AtomicLong::doubleValue)
                .description("Posts in a non-terminal AI review state")
                .register(meterRegistry);
        Gauge.builder("safety.pending.posts", safetyBacklog, AtomicLong::doubleValue)
                .description("Posts held in the audit queue by the safety fail-closed path")
                .register(meterRegistry);
        Gauge.builder("safety.pending.comments", commentSafetyBacklog, AtomicLong::doubleValue)
                .description("Visible comments awaiting a deferred comment-safety recheck")
                .register(meterRegistry);
        Gauge.builder("agent.mention.pending.replies", mentionReplyBacklog, AtomicLong::doubleValue)
                .description("Placeholder replies awaiting their real answer")
                .register(meterRegistry);
    }

    /**
     * Every 5 minutes, sweep stale AI review/safety states and re-trigger them.
     */
    @Scheduled(cron = "0 3/5 * * * ?")
    public void reconcile() {
        TraceIds.runAsJob("ai-review-reconcile", this::reconcileOnce);
    }

    private void reconcileOnce() {
        boolean reviewEnabled = aiProperties.getReview().isEnabled();
        boolean safetyEnabled = aiProperties.getSafety().isEnabled();
        boolean mentionEnabled = aiProperties.getMention().isEnabled();
        if (!reviewEnabled && !safetyEnabled && !mentionEnabled) {
            return;
        }
        // Backlog first, before any gate: an LLM outage is exactly the moment this number has to
        // be visible, and the health check below is where an unhealthy cycle returns early.
        refreshReviewBacklog();
        // Budget-exhausted sweep runs regardless of LLM health: those posts
        // are unclaimable dead state that no retry path will ever touch.
        if (reviewEnabled) {
            sweepBudgetExhaustedReviews();
        }
        if (!llmHealthCache.isHealthy()) {
            log.debug("[AI-RECONCILE] LLM unhealthy, skipping cycle.");
            return;
        }

        LocalDateTime staleBefore = LocalDateTime.now()
                .minusMinutes(aiProperties.getReconcile().getStaleMinutes());

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
                publishSafely(() -> eventPublisher.publishEvent(
                        new AiSafetyCheckEvent(this, post.getId(), post.getTitle(),
                                post.getContent(), post.getUserId())), "safety", post.getId());
            }
            countRepair("safety", pendingSafety.size());

            // ADR-0017: comments whose latest comment-safety row is a stale
            // pending-llm marker, still visible, get their deferred check now.
            List<Long> pendingComments = aiReviewLogMapper
                    .selectCommentIdsPendingSafetyRecheck(staleBefore, BATCH_LIMIT);
            for (Long commentId : pendingComments) {
                VibeComment comment = vibeCommentMapper.selectById(commentId);
                if (comment == null || comment.getStatus() == null
                        || comment.getStatus() != CommentStatus.VISIBLE.getCode()) {
                    continue;
                }
                log.info("[AI-RECONCILE] Re-running safety check for comment {}", commentId);
                publishSafely(() -> eventPublisher.publishEvent(
                        new AiCommentSafetyCheckEvent(this, comment.getId(), comment.getPostId(),
                                comment.getContent(), comment.getUserId())),
                        "comment-safety", comment.getId());
            }
            countRepair("comment_safety", pendingComments.size());
        }

        // ADR-0018: a mention that failed its first answer owes the thread a
        // real one. Placeholders whose latest mention-agent row is a stale
        // pending-llm marker get the reply re-run now; the listener replaces
        // the placeholder in place, so the thread never shows both.
        if (mentionEnabled) {
            List<Long> pendingPlaceholders = aiReviewLogMapper
                    .selectCommentIdsPendingMentionReplacement(staleBefore, BATCH_LIMIT);
            for (Long placeholderId : pendingPlaceholders) {
                VibeComment placeholder = vibeCommentMapper.selectById(placeholderId);
                if (placeholder == null || placeholder.getStatus() == null
                        || placeholder.getStatus() != CommentStatus.VISIBLE.getCode()
                        || placeholder.getUserId() == null
                        || placeholder.getUserId() != AgentReplyComments.AI_AGENT_USER_ID) {
                    continue;
                }
                VibeComment mention = placeholder.getParentId() == null
                        ? null : vibeCommentMapper.selectById(placeholder.getParentId());
                if (mention == null) {
                    // The mentioning comment was deleted; the placeholder has
                    // no question to answer anymore. Retire it as orphaned.
                    log.info("[AI-RECONCILE] Placeholder {} orphaned (mention gone), hiding it", placeholderId);
                    vibeCommentMapper.update(null, new LambdaUpdateWrapper<VibeComment>()
                            .eq(VibeComment::getId, placeholderId)
                            .eq(VibeComment::getStatus, CommentStatus.VISIBLE.getCode())
                            .set(VibeComment::getStatus, CommentStatus.HIDDEN.getCode()));
                    logOrphaned(placeholder.getPostId(), placeholderId);
                    continue;
                }
                Long mentionId = mention.getId();
                Long postId = mention.getPostId();
                String content = mention.getContent();
                Long authorId = mention.getUserId();
                log.info("[AI-RECONCILE] Re-running mention reply for placeholder {}", placeholderId);
                publishSafely(() -> eventPublisher.publishEvent(
                        new AgentMentionEvent(this, mentionId, postId, content, authorId, placeholderId)),
                        "mention", placeholderId);
            }
            countRepair("mention_reply", pendingPlaceholders.size());
        }
    }

    private void logOrphaned(Long postId, Long placeholderId) {
        try {
            AiReviewLog orphaned = new AiReviewLog();
            orphaned.setPostId(postId);
            orphaned.setCommentId(placeholderId);
            orphaned.setReviewer("mention-agent");
            orphaned.setResultJson("mentioning comment deleted");
            orphaned.setSeverity("orphaned");
            orphaned.setIsApproved(0);
            orphaned.setCreatedAt(LocalDateTime.now());
            aiReviewLogMapper.insert(orphaned);
        } catch (Exception e) {
            log.warn("Failed to log orphaned placeholder {}: {}", placeholderId, e.getMessage());
        }
    }

    /**
     * Publishing the gauge must never cost the reconcile cycle itself, so a failed count degrades
     * to "last value stands" with a warning rather than aborting the sweep.
     */
    private void refreshReviewBacklog() {
        try {
            reviewBacklog.set(vibePostMapper.countReviewsAwaitingWork());
        } catch (Exception e) {
            log.warn("[AI-RECONCILE] Failed to refresh review backlog gauge: {}", e.getMessage());
        }
        try {
            safetyBacklog.set(vibePostMapper.countSafetyPendingPosts());
        } catch (Exception e) {
            log.warn("[AI-RECONCILE] Failed to refresh safety backlog gauge: {}", e.getMessage());
        }
        try {
            commentSafetyBacklog.set(aiReviewLogMapper.countCommentsPendingSafetyRecheck());
        } catch (Exception e) {
            log.warn("[AI-RECONCILE] Failed to refresh comment safety backlog gauge: {}", e.getMessage());
        }
        try {
            mentionReplyBacklog.set(aiReviewLogMapper.countPendingMentionReplacements());
        } catch (Exception e) {
            log.warn("[AI-RECONCILE] Failed to refresh mention reply backlog gauge: {}", e.getMessage());
        }
    }

    private void countRepair(String kind, int amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder("ai.review.reconcile.repairs")
                .tag("kind", kind)
                .register(meterRegistry)
                .increment(amount);
    }

    private void countLeaseAttemptsExhausted() {
        Counter.builder("ai.review.lease.attempts.exhausted")
                .description("Reviews retired because the attempt budget was spent")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Retires posts stuck in REVIEWING with a spent attempt budget and an
     * expired lease: no claim can ever win them again, so they transition to
     * FAILED once (the conditional update also arbitrates concurrent task
     * instances — only the one that flips the row notifies the author).
     */
    private void sweepBudgetExhaustedReviews() {
        int maxAttempts = aiProperties.getReview().getMaxAttempts();
        List<VibePost> stuck = vibePostMapper.selectReviewingBudgetExhausted(maxAttempts, BATCH_LIMIT);
        int retired = 0;
        for (VibePost post : stuck) {
            int updated = vibePostMapper.update(null, new LambdaUpdateWrapper<VibePost>()
                    .eq(VibePost::getId, post.getId())
                    .eq(VibePost::getAiReviewed, AiReviewStatus.REVIEWING.getCode())
                    .set(VibePost::getAiReviewed, AiReviewStatus.FAILED.getCode()));
            if (updated > 0) {
                log.info("[AI-RECONCILE] Post {} REVIEWING with exhausted budget, retired to FAILED", post.getId());
                notifyBudgetExhausted(post);
                retired++;
                countLeaseAttemptsExhausted();
            }
        }
        countRepair("budget_exhausted", retired);
    }

    private void notifyBudgetExhausted(VibePost post) {
        if (post.getUserId() == null) {
            return;
        }
        try {
            sysMessageService.sendMessage(SysMessage.FROM_SYSTEM, post.getUserId(),
                    "你的帖子《" + post.getTitle() + "》的 AI 评审连续 "
                            + aiProperties.getReview().getMaxAttempts()
                            + " 次失败，已停止自动重试。内容本身不受影响；如需重新评审，请联系管理员。",
                    SysMessage.TYPE_SYSTEM);
        } catch (Exception e) {
            log.warn("Failed to notify author {} about exhausted review budget on post {}: {}",
                    post.getUserId(), post.getId(), e.getMessage());
        }
    }

    private int retriggerStaleReviews(AiReviewStatus status, LocalDateTime staleBefore) {
        List<VibePost> stalePosts = vibePostMapper.selectStaleAiReviewPosts(
                status.getCode(), staleBefore, aiProperties.getReview().getMaxAttempts(), BATCH_LIMIT);
        for (VibePost post : stalePosts) {
            log.info("[AI-RECONCILE] Re-triggering {} review for post {}", status, post.getId());
            publishSafely(() -> eventPublisher.publishEvent(
                    new AiReviewEvent(this, post.getId(), post.getTitle(),
                            post.getContent(), post.getUserId(), true)),
                    "review", post.getId());
        }
        countRepair(status.name().toLowerCase(), stalePosts.size());
        return stalePosts.size();
    }

    /**
     * A saturated agent pool rejects the async submission in the caller's
     * thread (as Spring's {@code TaskRejectedException}). Without this, one
     * rejection aborts the whole reconcile batch and the posts after it wait a
     * full cycle; with it, only that post is deferred. It is retried next
     * cycle either way, so the failure is logged and swallowed, not rethrown.
     */
    private void publishSafely(Runnable publish, String kind, Long postId) {
        try {
            publish.run();
        } catch (RuntimeException e) {
            log.warn("[AI-RECONCILE] Could not re-trigger {} for post {} (pool saturated?); will retry next cycle: {}",
                    kind, postId, e.getMessage());
        }
    }

}
