package com.nexus.campus.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiReviewLogMapper extends BaseMapper<AiReviewLog> {
    
    /**
     * Fetch the latest safety-check review log for a given post.
     * Only returns logs from the safety-check-agent reviewer.
     */
    @Select("SELECT * FROM ai_review_log " +
            "WHERE post_id = #{postId} AND reviewer = 'safety-check-agent' " +
            "ORDER BY created_at DESC LIMIT 1")
    AiReviewLog selectLatestSafetyLogByPostId(@Param("postId") Long postId);

    /**
     * Fetch the latest code-review log for a post.
     * Uses id DESC as a tie-breaker for legacy rows with identical timestamps.
     */
    @Select("SELECT * FROM ai_review_log " +
            "WHERE post_id = #{postId} AND reviewer = 'code-review-agent' " +
            "ORDER BY created_at DESC, id DESC LIMIT 1")
    AiReviewLog selectLatestCodeReviewByPostId(@Param("postId") Long postId);

    /**
     * Fetch code-review logs for a user's active posts, newest first.
     * Callers keep the first row per post to derive the latest review score.
     */
    @Select("SELECT l.* FROM ai_review_log l " +
            "INNER JOIN vibe_post p ON p.id = l.post_id " +
            "WHERE l.reviewer = 'code-review-agent' AND p.user_id = #{userId} AND p.status = 1 " +
            "ORDER BY l.created_at DESC, l.id DESC")
    List<AiReviewLog> selectLatestCodeReviewLogsByUserId(@Param("userId") Long userId);

    /**
     * Comment ids whose LATEST comment-safety log row is a pending-llm marker
     * older than the staleness bound, on a comment that is still visible.
     * The anti-join instead of a window function keeps this readable on any
     * MySQL the stack might run, and the comment join is what retires markers
     * on comments that were since deleted, hidden, or audited.
     */
    @Select("SELECT l.comment_id FROM ai_review_log l " +
            "INNER JOIN vibe_comment c ON c.id = l.comment_id AND c.status = 1 " +
            "LEFT JOIN ai_review_log newer " +
            "  ON newer.comment_id = l.comment_id " +
            " AND newer.reviewer = 'comment-safety-agent' " +
            " AND (newer.created_at > l.created_at " +
            "      OR (newer.created_at = l.created_at AND newer.id > l.id)) " +
            "WHERE l.reviewer = 'comment-safety-agent' " +
            "  AND l.severity = 'pending-llm' " +
            "  AND l.created_at < #{staleBefore} " +
            "  AND newer.id IS NULL " +
            "LIMIT #{limit}")
    List<Long> selectCommentIdsPendingSafetyRecheck(@Param("staleBefore") LocalDateTime staleBefore,
                                                    @Param("limit") int limit);

    /** Same population as {@link #selectCommentIdsPendingSafetyRecheck}, without the staleness bound, for the backlog gauge. */
    @Select("SELECT COUNT(DISTINCT l.comment_id) FROM ai_review_log l " +
            "INNER JOIN vibe_comment c ON c.id = l.comment_id AND c.status = 1 " +
            "LEFT JOIN ai_review_log newer " +
            "  ON newer.comment_id = l.comment_id " +
            " AND newer.reviewer = 'comment-safety-agent' " +
            " AND (newer.created_at > l.created_at " +
            "      OR (newer.created_at = l.created_at AND newer.id > l.id)) " +
            "WHERE l.reviewer = 'comment-safety-agent' " +
            "  AND l.severity = 'pending-llm' " +
            "  AND newer.id IS NULL")
    long countCommentsPendingSafetyRecheck();

    /**
     * Placeholder replies (agent-authored comments) whose LATEST mention-agent
     * row is a pending-llm marker older than the staleness bound. ADR-0018:
     * these are answered by replacing the placeholder in place, so the query
     * keys on the placeholder's own id — joined against the agent account and
     * still visible, exactly what the replacement will update.
     */
    @Select("SELECT l.comment_id FROM ai_review_log l " +
            "INNER JOIN vibe_comment c ON c.id = l.comment_id AND c.status = 1 AND c.user_id = 999 " +
            "LEFT JOIN ai_review_log newer " +
            "  ON newer.comment_id = l.comment_id " +
            " AND newer.reviewer = 'mention-agent' " +
            " AND (newer.created_at > l.created_at " +
            "      OR (newer.created_at = l.created_at AND newer.id > l.id)) " +
            "WHERE l.reviewer = 'mention-agent' " +
            "  AND l.severity = 'pending-llm' " +
            "  AND l.created_at < #{staleBefore} " +
            "  AND newer.id IS NULL " +
            "LIMIT #{limit}")
    List<Long> selectCommentIdsPendingMentionReplacement(@Param("staleBefore") LocalDateTime staleBefore,
                                                         @Param("limit") int limit);

    /** Same population as {@link #selectCommentIdsPendingMentionReplacement}, without the staleness bound, for the backlog gauge. */
    @Select("SELECT COUNT(DISTINCT l.comment_id) FROM ai_review_log l " +
            "INNER JOIN vibe_comment c ON c.id = l.comment_id AND c.status = 1 AND c.user_id = 999 " +
            "LEFT JOIN ai_review_log newer " +
            "  ON newer.comment_id = l.comment_id " +
            " AND newer.reviewer = 'mention-agent' " +
            " AND (newer.created_at > l.created_at " +
            "      OR (newer.created_at = l.created_at AND newer.id > l.id)) " +
            "WHERE l.reviewer = 'mention-agent' " +
            "  AND l.severity = 'pending-llm' " +
            "  AND newer.id IS NULL")
    long countPendingMentionReplacements();
}
