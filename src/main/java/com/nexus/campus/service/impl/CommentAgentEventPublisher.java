package com.nexus.campus.service.impl;

import com.nexus.campus.agent.AiCommentSafetyCheckEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.agent.LlmHealthCache;
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
import java.util.concurrent.RejectedExecutionException;

/**
 * Publishes comment safety-check events under the post-visible semantics of
 * ADR-0017: a comment is already public when the event fires, so there is no
 * fail-closed hold anywhere in this path. What replaces the hold is the
 * pending-llm marker — written when the LLM is unhealthy at enqueue or the
 * agent pool rejects the submission — which puts the comment on the
 * reconciliation path without ever hiding it.
 */
@Slf4j
@Component
public class CommentAgentEventPublisher {

    private final ApplicationEventPublisher events;
    private final LlmHealthCache llmHealthCache;
    private final AiReviewLogMapper reviewLogs;
    private final TransactionTemplate recovery;
    private final Counter enqueueUnhealthy;
    private final Counter poolRejected;

    public CommentAgentEventPublisher(ApplicationEventPublisher events,
                                      LlmHealthCache llmHealthCache,
                                      AiReviewLogMapper reviewLogs,
                                      PlatformTransactionManager transactionManager,
                                      MeterRegistry meterRegistry) {
        this.events = events;
        this.llmHealthCache = llmHealthCache;
        this.reviewLogs = reviewLogs;
        this.recovery = new TransactionTemplate(transactionManager);
        this.recovery.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Both series registered eagerly (ADR-0015): a check that never ran must
        // be visible as a counter at zero, not as an absent series.
        this.enqueueUnhealthy = Counter.builder("agent.comment.safety.enqueue.unhealthy")
                .description("Comments enqueued while the LLM was unhealthy; marker written, check deferred")
                .register(meterRegistry);
        this.poolRejected = Counter.builder("agent.comment.safety.pool.rejected")
                .description("Comment safety events the saturated agent pool refused; marker written")
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
