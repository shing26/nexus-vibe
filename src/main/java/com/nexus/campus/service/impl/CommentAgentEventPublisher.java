package com.nexus.campus.service.impl;

import com.nexus.campus.agent.AgentMentionEvent;
import com.nexus.campus.agent.AiCommentSafetyCheckEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.VibeComment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * Publishes comment safety-check events under the post-visible semantics of
 * ADR-0017: a comment is already public when the event fires, so there is no
 * fail-closed hold anywhere in this path. What replaces the hold is the
 * pending-llm marker — written when the LLM is unhealthy at enqueue or the
 * agent pool rejects the submission — which puts the comment on the
 * reconciliation path without ever hiding it.
 *
 * <p>The publisher also owns the mention trigger (ADR-0018): a comment whose
 * body summons {@code @ai-agent} dispatches an {@link AgentMentionEvent},
 * bounded by a per-user-per-post cooldown. Every failure on the dispatch
 * itself — unhealthy LLM at enqueue, saturated pool — is answered with a
 * visible placeholder, because a summon that vanishes is the one outcome
 * ADR-0018 forbids.</p>
 */
@Slf4j
@Component
public class CommentAgentEventPublisher {

    /** The summon token, matched case-insensitively inside the comment body. */
    static final String MENTION_TRIGGER = "@ai-agent";

    private final ApplicationEventPublisher events;
    private final LlmHealthCache llmHealthCache;
    private final AiReviewLogMapper reviewLogs;
    private final CampusAiProperties aiProperties;
    private final AgentReplyComments agentReplyComments;
    private final TransactionTemplate recovery;
    private final Counter enqueueUnhealthy;
    private final Counter poolRejected;
    private final Counter mentionEnqueueUnhealthy;
    private final Counter mentionPoolRejected;
    private final Counter mentionCooldownSuppressed;

    /**
     * Cooldown ledger for {@code userId:postId -> last summon}. In-memory by
     * design: it bounds cost, it is not durable state, and losing it on a
     * restart only lets one extra mention through per user per post.
     */
    private final Map<String, LocalDateTime> mentionCooldowns = new ConcurrentHashMap<>();

    public CommentAgentEventPublisher(ApplicationEventPublisher events,
                                      LlmHealthCache llmHealthCache,
                                      AiReviewLogMapper reviewLogs,
                                      CampusAiProperties aiProperties,
                                      AgentReplyComments agentReplyComments,
                                      PlatformTransactionManager transactionManager,
                                      MeterRegistry meterRegistry) {
        this.events = events;
        this.llmHealthCache = llmHealthCache;
        this.reviewLogs = reviewLogs;
        this.aiProperties = aiProperties;
        this.agentReplyComments = agentReplyComments;
        this.recovery = new TransactionTemplate(transactionManager);
        this.recovery.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // All series registered eagerly (ADR-0015): a check that never ran must
        // be visible as a counter at zero, not as an absent series.
        this.enqueueUnhealthy = Counter.builder("agent.comment.safety.enqueue.unhealthy")
                .description("Comments enqueued while the LLM was unhealthy; marker written, check deferred")
                .register(meterRegistry);
        this.poolRejected = Counter.builder("agent.comment.safety.pool.rejected")
                .description("Comment safety events the saturated agent pool refused; marker written")
                .register(meterRegistry);
        this.mentionEnqueueUnhealthy = Counter.builder("agent.mention.enqueue.unhealthy")
                .description("Mentions enqueued while the LLM was unhealthy; placeholder written directly")
                .register(meterRegistry);
        this.mentionPoolRejected = Counter.builder("agent.mention.pool.rejected")
                .description("Mention events the saturated agent pool refused; placeholder written")
                .register(meterRegistry);
        this.mentionCooldownSuppressed = Counter.builder("agent.mention.suppressed.cooldown")
                .description("Mentions dropped by the per-user-per-post cooldown")
                .register(meterRegistry);
    }

    public void publishSafetyCheck(VibeComment comment, Long authorId) {
        if (comment.getId() == null) {
            return;
        }
        // The health gate is a cost shortcut, not a safety hold: a comment is
        // visible either way, so an unhealthy LLM only means "write the marker
        // now instead of spending an async hop to fail".
        if (!llmHealthCache.isHealthy()) {
            log.warn("LLM unhealthy at comment enqueue, comment {} marked pending-llm", comment.getId());
            enqueueUnhealthy.increment();
            writePendingMarker(comment);
            return;
        }
        dispatchWhenCommitted(() -> {
            try {
                events.publishEvent(new AiCommentSafetyCheckEvent(this, comment.getId(),
                        comment.getPostId(), comment.getContent(), authorId));
            } catch (RejectedExecutionException e) {
                log.warn("Comment safety queue rejected comment {}, marked pending-llm: {}",
                        comment.getId(), e.getMessage());
                poolRejected.increment();
                recovery.executeWithoutResult(status -> writePendingMarker(comment));
            }
        });
    }

    /**
     * The mention trigger. Called on the comment-creation path next to
     * {@link #publishSafetyCheck}; agent replies never pass through here (the
     * reply writer inserts through the mapper), and the author check below is
     * defense in depth on top of that.
     */
    public void publishMentionReply(VibeComment comment, Long authorId) {
        if (comment.getId() == null || comment.getContent() == null) {
            return;
        }
        if (authorId != null && authorId == AgentReplyComments.AI_AGENT_USER_ID) {
            return;
        }
        if (!aiProperties.getMention().isEnabled()) {
            return;
        }
        if (!comment.getContent().toLowerCase().contains(MENTION_TRIGGER)) {
            return;
        }
        if (!tryAcquireCooldown(authorId, comment.getPostId())) {
            log.info("Mention on post {} by user {} suppressed by the cooldown", comment.getPostId(), authorId);
            mentionCooldownSuppressed.increment();
            return;
        }
        // Same cost-shortcut rule as the safety path — with one difference the
        // ADR-0018 inversion forces: an unhealthy LLM still owes the thread a
        // visible placeholder, so the placeholder is written right here rather
        // than deferred to the listener.
        if (!llmHealthCache.isHealthy()) {
            log.warn("LLM unhealthy at mention enqueue, placeholder written directly for comment {}", comment.getId());
            mentionEnqueueUnhealthy.increment();
            // Same transaction as the mentioning comment: the placeholder and
            // the mention commit together or not at all.
            agentReplyComments.insertPlaceholder(comment.getPostId(), comment.getId(), "LLM unhealthy at enqueue");
            return;
        }
        dispatchWhenCommitted(() -> {
            try {
                events.publishEvent(new AgentMentionEvent(this, comment.getId(),
                        comment.getPostId(), comment.getContent(), authorId, null));
            } catch (RejectedExecutionException e) {
                log.warn("Agent pool rejected the mention reply for comment {}, writing placeholder: {}",
                        comment.getId(), e.getMessage());
                mentionPoolRejected.increment();
                recovery.executeWithoutResult(status ->
                        agentReplyComments.insertPlaceholder(comment.getPostId(), comment.getId(), "agent pool saturated"));
            }
        });
    }

    /**
     * Returns true when this summon is outside the cooldown and records it.
     * Entries past the window are dropped inline, so the map cannot grow
     * without bound.
     */
    private boolean tryAcquireCooldown(Long userId, Long postId) {
        if (userId == null || postId == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusSeconds(aiProperties.getMention().getCooldownSeconds());
        mentionCooldowns.values().removeIf(seen -> seen.isBefore(windowStart));
        String key = userId + ":" + postId;
        LocalDateTime last = mentionCooldowns.put(key, now);
        return last == null || last.isBefore(windowStart);
    }

    /**
     * Same commit-deferral rule as {@link PostAgentEventPublisher}: the
     * listener must see the committed comment, and the pool rejection surfaces
     * inside the dispatch where the catch above can reach it.
     */
    private void dispatchWhenCommitted(Runnable dispatch) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
            return;
        }
        dispatch.run();
    }

    private void writePendingMarker(VibeComment comment) {
        try {
            AiReviewLog marker = new AiReviewLog();
            marker.setPostId(comment.getPostId());
            marker.setCommentId(comment.getId());
            marker.setReviewer("comment-safety-agent");
            marker.setResultJson("deferred at enqueue");
            marker.setSeverity("pending-llm");
            marker.setIsApproved(0);
            marker.setCreatedAt(LocalDateTime.now());
            reviewLogs.insert(marker);
        } catch (Exception ex) {
            log.warn("Failed to write pending-llm marker for comment {}: {}", comment.getId(), ex.getMessage());
        }
    }
}

