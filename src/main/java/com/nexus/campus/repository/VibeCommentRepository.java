package com.nexus.campus.repository;

import com.nexus.campus.entity.VibeComment;

public interface VibeCommentRepository {

    void insert(VibeComment comment);

    /**
     * Hides the AI reviewer's earlier comments on a post by flipping their status
     * to 0. The marker identifies those comments by their opening line, and the
     * wrapper that expresses that filter stays in the adapter.
     */
    void hideAiReviewComments(Long postId, long reviewerUserId, String marker);

    void deleteByPost(Long postId);
}
