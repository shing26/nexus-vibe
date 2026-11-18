package com.nexus.campus.repository;

import com.nexus.campus.agent.AiReviewLog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiReviewLogRepository {

    void insert(AiReviewLog log);

    void deleteByPost(Long postId);

    Optional<AiReviewLog> findLatestSafety(Long postId);

    /**
     * Latest code-review-agent row per post, for a whole page of posts in one
     * query. Posts without a review row are simply absent from the result.
     */
    List<AiReviewLog> findLatestCodeReview(Collection<Long> postIds);
}
