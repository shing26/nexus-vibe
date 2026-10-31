package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;

/**
 * The default policy for code posts: fenced code blocks decide. Prompt
 * templates are excluded because they are claimed by
 * {@link PromptReviewPolicy} instead.
 *
 * <p>Kept as a plain class resolved by ReviewPolicyConfig so a fork replaces
 * it with one bean definition rather than with a component scan exclusion.</p>
 */
public class CodeBlockReviewPolicy implements ReviewPolicy {

    private final CodeReviewReviewer codeReviewer;

    public CodeBlockReviewPolicy(CodeReviewReviewer codeReviewer) {
        this.codeReviewer = codeReviewer;
    }

    @Override
    public boolean shouldReview(VibePost post) {
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            return false;
        }
        if ("prompt".equals(post.getPostType())) {
            return false;
        }
        return !codeReviewer.detectCodeBlocks(post.getContent()).isEmpty();
    }
}
