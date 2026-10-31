package com.nexus.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Every comment the agent posts in the mention flow (ADR-0018): the real
 * reply, the visible placeholder that answers a failed attempt, and the
 * in-place replacement reconciliation performs. Agent comments are inserted
 * through the mapper directly — never through the comment service — which is
 * what keeps an agent reply from re-triggering safety checks or further
 * agent work.
 */
@Slf4j
@Component
public class AgentReplyComments {

    /** The AiAgent system account that posts replies. */
    public static final long AI_AGENT_USER_ID = 999L;

    /**
     * First line of a placeholder reply. The text is shown to users as-is and
     * is how a human can tell a waiting reply from a missing one.
     */
    public static final String PLACEHOLDER_TEXT =
            "AI 助手暂时无法回复，系统会自动重试；回复会出现在这条消息的位置。";

    private final VibeCommentMapper vibeCommentMapper;
    private final VibePostMapper vibePostMapper;
    private final AiReviewLogMapper reviewLogs;

    public AgentReplyComments(VibeCommentMapper vibeCommentMapper,
                              VibePostMapper vibePostMapper,
                              AiReviewLogMapper reviewLogs) {
        this.vibeCommentMapper = vibeCommentMapper;
        this.vibePostMapper = vibePostMapper;
        this.reviewLogs = reviewLogs;
    }

    /** Inserts the agent's reply threaded under the mentioning comment. */
    public VibeComment insertReply(Long postId, Long mentionCommentId, String content) {
        VibeComment reply = new VibeComment();
        reply.setPostId(postId);
        reply.setUserId(AI_AGENT_USER_ID);
        reply.setParentId(mentionCommentId == null ? 0L : mentionCommentId);
        reply.setTargetId(0L);
        reply.setContent(content);
        reply.setStatus(1);
        vibeCommentMapper.insert(reply);
        vibePostMapper.recalculateCommentCount(postId);
        log.info("Agent reply {} posted for comment {} on post {}", reply.getId(), mentionCommentId, postId);
        return reply;
    }

    /**
     * The failure answer ADR-0018 demands: a visible placeholder naming the
     * outage, plus the pending-llm marker keyed to the placeholder's own id —
     * that marker is what reconciliation later resolves by replacing the
     * placeholder in place.
     */
    public VibeComment insertPlaceholder(Long postId, Long mentionCommentId, String reason) {
        VibeComment placeholder = insertReply(postId, mentionCommentId, PLACEHOLDER_TEXT);
        try {
            AiReviewLog marker = new AiReviewLog();
            marker.setPostId(postId);
            marker.setCommentId(placeholder.getId());
            marker.setReviewer("mention-agent");
            marker.setResultJson(reason == null ? "LLM unavailable" : reason);
            marker.setSeverity("pending-llm");
            marker.setIsApproved(0);
            marker.setCreatedAt(LocalDateTime.now());
            reviewLogs.insert(marker);
        } catch (Exception e) {
            log.warn("Failed to write pending-llm marker for placeholder {}: {}",
                    placeholder.getId(), e.getMessage());
        }
        return placeholder;
    }

    /**
     * In-place replacement: the placeholder's row is updated, never superseded
     * by a second comment. Conditional on the agent authorship so a human's
     * edit of that comment cannot be silently overwritten.
     */
    public int replacePlaceholder(Long placeholderCommentId, String content) {
        return vibeCommentMapper.update(null, new LambdaUpdateWrapper<VibeComment>()
                .eq(VibeComment::getId, placeholderCommentId)
                .eq(VibeComment::getUserId, AI_AGENT_USER_ID)
                .eq(VibeComment::getStatus, 1)
                .set(VibeComment::getContent, content));
    }

    /**
     * Resolves a placeholder's pending-llm marker by writing the newer
     * mention-agent row the anti-join in {@link AiReviewLogMapper} looks for.
     * A placeholder whose mentioning comment was deleted in between is
     * retired as orphaned instead of retried forever.
     */
    public void logAnswered(Long postId, Long placeholderCommentId, String rawReply) {
        try {
            AiReviewLog answered = new AiReviewLog();
            answered.setPostId(postId);
            answered.setCommentId(placeholderCommentId);
            answered.setReviewer("mention-agent");
            answered.setResultJson(truncateForLog(rawReply));
            answered.setSeverity("answered");
            answered.setIsApproved(1);
            answered.setCreatedAt(LocalDateTime.now());
            reviewLogs.insert(answered);
        } catch (Exception e) {
            log.warn("Failed to log answered mention for placeholder {}: {}",
                    placeholderCommentId, e.getMessage());
        }
    }

    private String truncateForLog(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.length() <= 2000 ? raw : raw.substring(0, 2000);
    }
}
