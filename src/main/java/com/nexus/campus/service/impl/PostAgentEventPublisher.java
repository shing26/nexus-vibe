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

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class PostAgentEventPublisher {

    private final ApplicationEventPublisher events;
    private final VibePostRepository posts;
    private final AiReviewLogRepository reviewLogs;
    private final LlmHealthCache llmHealthCache;

    public PostAgentEventPublisher(ApplicationEventPublisher events,
                                   VibePostRepository posts,
                                   AiReviewLogRepository reviewLogs,
                                   LlmHealthCache llmHealthCache) {
        this.events = events;
        this.posts = posts;
        this.reviewLogs = reviewLogs;
        this.llmHealthCache = llmHealthCache;
    }

    public void publishReview(VibePost post, Long userId) {
        try {
            events.publishEvent(new AiReviewEvent(this, post.getId(), post.getTitle(),
                    post.getContent(), userId));
        } catch (RejectedExecutionException e) {
            log.warn("AI review queue saturated, post {} marked FAILED for reconciliation", post.getId());
            try {
                VibePost failed = new VibePost();
                failed.setId(post.getId());
                failed.setAiReviewed(AiReviewStatus.FAILED.getCode());
                posts.update(failed);
            } catch (Exception ex) {
                log.warn("Failed to mark post {} FAILED after rejection: {}", post.getId(), ex.getMessage());
            }
        }
    }

    public void publishSafety(VibePost post, Long userId) {
        if (!llmHealthCache.isHealthy()) {
            log.warn("LLM unhealthy at enqueue, post {} failed closed to PENDING_REVIEW", post.getId());
            failClosedAtEnqueue(post, "LLM unhealthy at enqueue");
            return;
        }
        try {
            events.publishEvent(new AiSafetyCheckEvent(this, post.getId(), post.getTitle(),
                    post.getContent(), userId));
        } catch (RejectedExecutionException e) {
            log.warn("Safety check queue saturated, post {} failed closed to PENDING_REVIEW", post.getId());
            failClosedAtEnqueue(post, "pipeline saturated at enqueue");
        }
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
}
