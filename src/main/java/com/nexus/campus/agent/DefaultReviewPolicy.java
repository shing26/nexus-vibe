package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;

/**
 * The default trigger rule: a post is reviewable when either reviewer claims
 * it — fenced code blocks for code review, {@code postType=prompt} for prompt
 * review. The two delegated policies are mutually exclusive by construction,
 * so a prompt post containing a code block is claimed by exactly one reviewer.
 *
 * <p>Kept as a plain class resolved by ReviewPolicyConfig (ADR-0012): a fork
 * replaces the whole trigger rule with one bean definition.</p>
 */
public class DefaultReviewPolicy implements ReviewPolicy {

    private final CodeBlockReviewPolicy code;
    private final PromptReviewPolicy prompt;

    public DefaultReviewPolicy(CodeBlockReviewPolicy code, PromptReviewPolicy prompt) {
        this.code = code;
        this.prompt = prompt;
    }

    @Override
    public boolean shouldReview(VibePost post) {
        return code.shouldReview(post) || prompt.shouldReview(post);
    }
}
