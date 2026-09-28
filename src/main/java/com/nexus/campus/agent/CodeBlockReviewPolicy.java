package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;

/**
 * The default policy: fenced code blocks decide. Prompt templates are
 * excluded because they are reusable instructions, not code submissions.
 *
 * <p>Kept as a plain class resolved by ReviewPolicyConfig so a fork replaces
 * it with one bean definition rather than with a component scan exclusion.</p>
 */
public class CodeBlockReviewPolicy implements ReviewPolicy {

    private final AiReviewService aiReviewService;

    public CodeBlockReviewPolicy(AiReviewService aiReviewService) {
        this.aiReviewService = aiReviewService;
    }

    @Override
    public boolean shouldReview(VibePost post) {
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            return false;
        }
        if ("prompt".equals(post.getPostType())) {
            return false;
        }
        return !aiReviewService.detectCodeBlocks(post.getContent()).isEmpty();
    }
}
