package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;

/**
 * Claims prompt posts for review. The counterpart of
 * {@link CodeBlockReviewPolicy}: where code policy excludes
 * {@code postType=prompt}, this one claims exactly those. Kept as a plain
 * class resolved by ReviewPolicyConfig, like its sibling.
 */
public class PromptReviewPolicy implements ReviewPolicy {

    @Override
    public boolean shouldReview(VibePost post) {
        return post != null
                && post.getStatus() != null
                && post.getStatus() == 1
                && "prompt".equals(post.getPostType());
    }
}
