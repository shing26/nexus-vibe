package com.nexus.campus.agent;

import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiReviewEventListener {

    @Autowired
    private AiReviewService aiReviewService;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Value("${campus.ai.review.enabled:true}")
    private boolean reviewEnabled;

    @Async
    @EventListener
    public void handleAiReviewEvent(AiReviewEvent event) {
        if (!reviewEnabled) {
            log.debug("AI review is disabled, skipping post {}", event.getPostId());
            return;
        }

        Long postId = event.getPostId();
        String content = event.getContent();

        // Check if content has code blocks
        if (aiReviewService.detectCodeBlocks(content).isEmpty()) {
            log.debug("No code blocks in post {}, skipping AI review", postId);
            return;
        }

        // Mark as in progress (2 = reviewing)
        try {
            VibePost post = vibePostMapper.selectById(postId);
            if (post != null) {
                post.setAiReviewed(AiReviewStatus.REVIEWING.getCode());
                vibePostMapper.updateById(post);
            }
        } catch (Exception e) {
            log.warn("Failed to update ai_reviewed status for post {}: {}", postId, e.getMessage());
        }

        try {
            // Run the review
            aiReviewService.reviewPost(postId, content);

            // Mark as complete (1 = reviewed, approved/pending); keep the score AiReviewService persisted
            VibePost post = vibePostMapper.selectById(postId);
            if (post != null) {
                post.setAiReviewed(AiReviewStatus.REVIEWED.getCode());
                vibePostMapper.updateById(post);
            }
            log.info("AI review completed for post {}", postId);

        } catch (Exception e) {
            log.warn("AI review failed for post {}: {}", postId, e.getMessage());

            // Mark as failed (0 = not reviewed, will retry)
            try {
                VibePost post = vibePostMapper.selectById(postId);
                if (post != null) {
                    post.setAiReviewed(AiReviewStatus.NOT_REVIEWED.getCode());
                    vibePostMapper.updateById(post);
                }
            } catch (Exception ex) {
                log.warn("Failed to reset ai_reviewed status for post {}: {}", postId, ex.getMessage());
            }
        }
    }

}
