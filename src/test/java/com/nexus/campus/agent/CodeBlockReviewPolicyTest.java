package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeBlockReviewPolicyTest {

    @Mock
    private AiReviewService aiReviewService;

    private CodeBlockReviewPolicy policy;

    private VibePost post;

    @BeforeEach
    void setUp() {
        policy = new CodeBlockReviewPolicy(aiReviewService);
        post = new VibePost();
        post.setStatus(1);
        post.setPostType("post");
        post.setContent("plain text");
    }

    @Test
    @DisplayName("A visible post with a fenced code block is reviewed")
    void visiblePostWithCodeBlockIsReviewed() {
        when(aiReviewService.detectCodeBlocks("plain text")).thenReturn(List.of("int x = 1;"));

        assertTrue(policy.shouldReview(post));
    }

    @Test
    @DisplayName("A post without a code block is not reviewed")
    void postWithoutCodeBlockIsNotReviewed() {
        when(aiReviewService.detectCodeBlocks("plain text")).thenReturn(List.of());

        assertFalse(policy.shouldReview(post));
    }

    @Test
    @DisplayName("A prompt template is never reviewed, code block or not")
    void promptTemplateIsNeverReviewed() {
        post.setPostType("prompt");

        assertFalse(policy.shouldReview(post));
    }

    @Test
    @DisplayName("A post that is not publicly visible is not reviewed")
    void hiddenPostIsNotReviewed() {
        post.setStatus(2);

        assertFalse(policy.shouldReview(post));
    }
}
