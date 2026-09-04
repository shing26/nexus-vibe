package com.nexus.campus.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
}
