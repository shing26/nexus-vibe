package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default trigger rule and its prompt-side half. The acceptance case this
 * pair exists for: a prompt post that also contains a code block is claimed
 * by exactly one reviewer — the code policy excludes it, the prompt policy
 * claims it, and the default rule ORs the two.
 */
class DefaultReviewPolicyTest {

    private final CodeBlockReviewPolicy codePolicy =
            new CodeBlockReviewPolicy(new CodeReviewReviewer(null, null));
    private final DefaultReviewPolicy policy = new DefaultReviewPolicy(codePolicy, new PromptReviewPolicy());

    private VibePost post(String postType, String content) {
        VibePost post = new VibePost();
        post.setStatus(1);
        post.setPostType(postType);
        post.setContent(content);
        return post;
    }

    @Test
    @DisplayName("A visible prompt post is reviewable, with or without code blocks")
    void visiblePromptPostIsReviewable() {
        assertTrue(policy.shouldReview(post("prompt", "plain instructions")));
        assertTrue(policy.shouldReview(post("prompt", "```java\nint x = 1;\n```")));
    }

    @Test
    @DisplayName("A code post is reviewable only when it carries a fenced block")
    void codePostNeedsAFencedBlock() {
        assertTrue(policy.shouldReview(post("post", "```java\nint x = 1;\n```")));
        assertFalse(policy.shouldReview(post("post", "plain text")));
    }

    @Test
    @DisplayName("Null and non-visible posts are not reviewable")
    void ineligiblePostsAreNotReviewable() {
        assertFalse(policy.shouldReview(null));
        VibePost hidden = post("post", "```java\nint x = 1;\n```");
        hidden.setStatus(2);
        assertFalse(policy.shouldReview(hidden));
    }
}
