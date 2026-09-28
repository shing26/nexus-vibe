package com.nexus.campus.repository;

import com.nexus.campus.agent.AiReviewLog;

import java.util.Optional;

public interface AiReviewLogRepository {

    void insert(AiReviewLog log);

    void deleteByPost(Long postId);

    Optional<AiReviewLog> findLatestSafety(Long postId);
}
