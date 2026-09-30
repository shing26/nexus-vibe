package com.nexus.campus.service.impl;

import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.enums.PostStatus;
import com.nexus.campus.repository.AiReviewLogRepository;
import com.nexus.campus.repository.VibePostRepository;
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

@Slf4j
@Component
public class PostAgentEventPublisher {

    private final ApplicationEventPublisher events;
    private final VibePostRepository posts;
    private final AiReviewLogRepository reviewLogs;
    private final LlmHealthCache llmHealthCache;
    private final TransactionTemplate saturationRecovery;

    public PostAgentEventPublisher(ApplicationEventPublisher events,
                                   VibePostRepository posts,
                                   AiReviewLogRepository reviewLogs,
                                   LlmHealthCache llmHealthCache,
                                   PlatformTransactionManager transactionManager) {
        this.events = events;
        this.posts = posts;
        this.reviewLogs = reviewLogs;
        this.llmHealthCache = llmHealthCache;
        // The recovery writes run once the creating transaction has finished, so they need a
        // transaction of their own; on the outer one they would never be committed.
        this.saturationRecovery = new TransactionTemplate(transactionManager);
        this.saturationRecovery.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void publishReview(VibePost post, Long userId) {
        dispatchWhenCommitted(() -> {
            try {
                events.publishEvent(new AiReviewEvent(this, post.getId(), post.getTitle(),
                        post.getContent(), userId));
            } catch (RejectedExecutionException e) {
                log.warn("AI review queue saturated, post {} marked FAILED for reconciliation", post.getId());
                inRecoveryTransaction(() -> markFailedForReconciliation(post.getId()));
            }
        });
    }

    public void publishSafety(VibePost post, Long userId) {
        // The health gate stays inline, inside the caller's transaction: holding a post closed
        // has to commit together with the insert that published it. Deferring it would leave a
        // window where the post is publicly visible before anything holds it.
        if (!llmHealthCache.isHealthy()) {
            log.warn("LLM unhealthy at enqueue, post {} failed closed to PENDING_REVIEW", post.getId());
            failClosedAtEnqueue(post, "LLM unhealthy at enqueue");
            return;
        }
        dispatchWhenCommitted(() -> {
            try {
                events.publishEvent(new AiSafetyCheckEvent(this, post.getId(), post.getTitle(),
                        post.getContent(), userId));
            } catch (RejectedExecutionException e) {
                log.warn("Safety check queue saturated, post {} failed closed to PENDING_REVIEW", post.getId());
                inRecoveryTransaction(() -> failClosedAtEnqueue(post, "pipeline saturated at enqueue"));
            }
        });
    }

    /**
     * Runs the dispatch once the creating transaction has committed.
     *
     * <p>The agent listeners are plain {@code @EventListener}s, so publishing inline would hand
     * them a post the rest of the system cannot see yet. A listener that finds no row concludes
     * the post is ineligible and clears its REVIEWING marker — and if the transaction commits
     * between that read and that write, the marker is reset on the row that was just created.
     * Nothing reconciles a post left at {@code ai_reviewed = 0}, so it would never be reviewed.
     * Waiting for the commit removes the race.</p>
     *
     * <p>It is also the only place the saturation handling can work. The @Async submission is
     * what a full agent pool rejects, and it happens inside the listener, so it must happen
     * somewhere the catch above can see. Had the listeners been
     * {@code @TransactionalEventListener(AFTER_COMMIT)} instead, the rejection would be raised
     * inside Spring's completion callback — outside this try block and outside the caller's
     * transaction, leaving a committed, publicly visible post with no safety verdict.</p>
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

    private void inRecoveryTransaction(Runnable recovery) {
        saturationRecovery.executeWithoutResult(status -> recovery.run());
    }

    private void failClosedAtEnqueue(VibePost post, String reason) {
        try {
            posts.updateStatus(post.getId(), PostStatus.PENDING_REVIEW.getCode());
            AiReviewLog marker = new AiReviewLog();
            marker.setPostId(post.getId());
            marker.setReviewer("safety-check-agent");
            marker.setResultJson(reason);
            marker.setSeverity("pending-llm");
            marker.setIsApproved(0);
            marker.setCreatedAt(LocalDateTime.now());
            reviewLogs.insert(marker);
        } catch (Exception ex) {
            log.warn("Failed to fail-closed post {} at enqueue: {}", post.getId(), ex.getMessage());
        }
    }

    private void markFailedForReconciliation(Long postId) {
        try {
            VibePost failed = new VibePost();
            failed.setId(postId);
            failed.setAiReviewed(AiReviewStatus.FAILED.getCode());
            posts.update(failed);
        } catch (Exception ex) {
            log.warn("Failed to mark post {} FAILED after rejection: {}", postId, ex.getMessage());
        }
    }
}
