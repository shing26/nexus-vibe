package com.nexus.campus.agent;

import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.SysMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Async listener for {@link AiReviewEvent}, running on the dedicated
 * agent-llm pool (isolated from message/notification work).
 *
 * <p>Every attempt first claims a lease on the post ({@code tryClaimReview},
 * ADR-0005): the claim is an atomic conditional UPDATE, so concurrent
 * instances or a re-published event cannot double-process a post. When the
 * attempt budget is exhausted the claim is refused; the terminal notification
 * is sent once — by the failed attempt that consumed the final budget.</p>
 */
@Slf4j
@Component
public class AiReviewEventListener {

    @Autowired
    private AiReviewService aiReviewService;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private SysMessageService sysMessageService;

    @Value("${campus.ai.review.enabled:true}")
    private boolean reviewEnabled;

    @Value("${campus.ai.review.lease-seconds:30}")
    private long leaseSeconds;

    @Value("${campus.ai.review.max-attempts:5}")
    private int maxAttempts;

    @Value("${campus.ai.review.owner-id:}")
    private String ownerId;

    @Async("agentLlmExecutor")
    @EventListener
    public void handleAiReviewEvent(AiReviewEvent event) {
        if (!reviewEnabled) {
            log.debug("AI review is disabled, skipping post {}", event.getPostId());
            return;
        }

        Long postId = event.getPostId();
        String content = event.getContent();

        if (aiReviewService.detectCodeBlocks(content).isEmpty()) {
            log.debug("No code blocks in post {}, skipping AI review", postId);
            return;
        }

        // Atomic lease claim: wins iff no live lock and attempts remain.
        int attempts = claim(postId);
        if (attempts < 0) {
            log.debug("Review lease for post {} held elsewhere or budget exhausted, skipping", postId);
            return;
        }

        markReviewing(postId);

        try {
            aiReviewService.reviewPost(postId, event.getTitle(), content, event.getAuthorId(), event.isRetried());
            log.info("AI review completed for post {} (attempt {})", postId, attempts);

        } catch (Exception e) {
            log.warn("AI review failed for post {}: {}", postId, e.getMessage());
            try {
                VibePost failed = new VibePost();
                failed.setId(postId);
                failed.setAiReviewed(AiReviewStatus.FAILED.getCode());
                vibePostMapper.updateById(failed);
            } catch (Exception ex) {
                log.warn("Failed to mark post {} FAILED: {}", postId, ex.getMessage());
            }

            if (attempts >= maxAttempts) {
                notifyBudgetExhausted(postId, event.getTitle(), event.getAuthorId());
            }
        }
    }

    /**
     * Atomic claim; returns the new attempt count, or -1 when the lease is
     * held elsewhere or the attempt budget is exhausted.
     */
    private int claim(Long postId) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = vibePostMapper.tryClaimReview(
                postId, now.plusSeconds(leaseSeconds), resolveOwner(), now, maxAttempts);
        if (claimed <= 0) {
            return -1;
        }
        Integer attempts = vibePostMapper.selectReviewAttempts(postId);
        return attempts != null ? attempts : 1;
    }

    private String resolveOwner() {
        if (ownerId != null && !ownerId.isBlank()) {
            return ownerId;
        }
        return System.getenv().getOrDefault("HOSTNAME", "local") + ":" + ProcessHandle.current().pid();
    }

    private void markReviewing(Long postId) {
        try {
            VibePost post = new VibePost();
            post.setId(postId);
            post.setAiReviewed(AiReviewStatus.REVIEWING.getCode());
            vibePostMapper.updateById(post);
        } catch (Exception e) {
            log.warn("Failed to mark post {} REVIEWING: {}", postId, e.getMessage());
        }
    }

    private void notifyBudgetExhausted(Long postId, String title, Long authorId) {
        if (authorId == null) {
            return;
        }
        try {
            sysMessageService.sendMessage(SysMessage.FROM_SYSTEM, authorId,
                    "你的帖子《" + title + "》的 AI 评审连续 " + maxAttempts
                            + " 次失败，已停止自动重试。内容本身不受影响；如需重新评审，请联系管理员。",
                    SysMessage.TYPE_SYSTEM);
        } catch (Exception e) {
            log.warn("Failed to notify author {} about exhausted review budget on post {}: {}",
                    authorId, postId, e.getMessage());
        }
    }
}
