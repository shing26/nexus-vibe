package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;

/**
 * Answers one question: should this post be submitted to the code-review
 * pipeline? This is the seam a fork replaces when its subject no longer means
 * "a post with fenced code blocks" (ADR-0012).
 */
public interface ReviewPolicy {

    /**
     * The post passed here is the row after the caller applied its own edits.
     * Returning {@code false} must leave the post alone; core still owns the
     * lease, the retry budget and the terminal state.
     */
    boolean shouldReview(VibePost post);
}
