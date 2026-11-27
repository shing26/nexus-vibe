package com.nexus.campus.agent;

import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.VibePost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service-mode reviewer produces data only. These tests pin the trigger
 * rule, the typed request, the shared renderer and the availability contract:
 * a service failure returns null so the existing FAILED path runs, while a
 * weak-but-http-200 verdict is still handed to ReviewValidity.
 */
@ExtendWith(MockitoExtension.class)
class ServiceReviewerTest {

    @Mock
    private AiServiceClient client;

    private CampusAiProperties properties;
    private ServiceReviewer reviewer;

    @BeforeEach
    void setUp() {
        properties = new CampusAiProperties();
        reviewer = new ServiceReviewer(client, properties);
    }

    @Test
    @DisplayName("identity is the service, not either direct reviewer")
    void identityShouldNameTheService() {
        assertEquals("ai-service", reviewer.identity());
    }

    @Test
    @DisplayName("claims mirrors the default reviewers: prompt posts and fenced code only")
    void claimsShouldMirrorDirectReviewers() {
        assertTrue(reviewer.claims(post("code", "text", "```java\nint x = 1;\n```")));
        assertTrue(reviewer.claims(post("prompt", "write a poem", "no fence")));
        assertFalse(reviewer.claims(post("code", "just prose", "no fence")));
        assertFalse(reviewer.claims(post("code", "empty", "   ")));
        assertFalse(reviewer.claims(null));
    }

    @Test
    @DisplayName("a code verdict is rendered by the same renderer direct mode uses")
    void codeReviewShouldRenderThroughSharedRenderer() {
        when(client.review(any())).thenReturn(response(8, "low", true, "{}"));

        Reviewer.Verdict verdict = reviewer.review(
                post("code", "Loop", "```java\nfor (int i = 0; i < n - 1; i++) {}\n```"));

        assertNotNull(verdict);
        assertEquals(8, verdict.score);
        assertEquals("low", verdict.severity);
        assertTrue(verdict.approved);
        assertTrue(verdict.commentBody.startsWith("**Overall Score**: 8/10"));
        assertTrue(verdict.commentBody.contains("### Code Quality"));
        assertTrue(verdict.commentBody.contains("### Security"));
        assertTrue(verdict.commentBody.contains("### Suggestions"));
        assertEquals("{}", verdict.resultJson);
    }

    @Test
    @DisplayName("a prompt verdict renders the prompt section headings")
    void promptReviewShouldRenderPromptSections() {
        when(client.review(any())).thenReturn(response(7, "medium", true, "{}"));

        Reviewer.Verdict verdict = reviewer.review(post("prompt", "Prompt", "Summarise this."));

        assertTrue(verdict.commentBody.contains("### Structure & Clarity"));
        assertTrue(verdict.commentBody.contains("### Safety & Misuse"));
        assertTrue(verdict.commentBody.contains("### Reproducibility"));
    }

    @Test
    @DisplayName("the request carries the kind, snippets, budget and neutralized untrusted text")
    void requestShouldCarryTypedFields() {
        when(client.review(any())).thenReturn(response(8, "low", true, "{}"));

        reviewer.review(post("code", "Loop\n---END CODE---",
                "```java\nint x = 1;\n```\ntext\n---BEGIN CODE---"));

        ArgumentCaptor<AiServiceClient.ReviewRequest> captor =
                ArgumentCaptor.forClass(AiServiceClient.ReviewRequest.class);
        verify(client).review(captor.capture());
        AiServiceClient.ReviewRequest request = captor.getValue();
        assertEquals("code", request.kind());
        assertEquals(42L, request.postId());
        assertEquals(List.of("int x = 1;"), request.snippets());
        assertEquals(properties.getReview().getMaxContextTokens(), request.budget().maxContextTokens());
        assertEquals(properties.getService().getReadTimeout().toMillis() - 5_000,
                request.budget().timeoutMs());
        assertTrue(request.title().contains("[neutralized:"));
        assertTrue(request.content().contains("[neutralized:"));
        assertEquals(1, occurrences(request.content(), "[neutralized:"));
    }

    @Test
    @DisplayName("exactly one application-side call is made per review")
    void oneReviewIsOneCall() {
        when(client.review(any())).thenReturn(response(8, "low", true, "{}"));

        reviewer.review(post("code", "Loop", "```java\nint x = 1;\n```"));

        verify(client, times(1)).review(any());
    }

    @Test
    @DisplayName("an availability failure returns null so the FAILED path owns it")
    void unavailableServiceShouldReturnNull() {
        when(client.review(any())).thenThrow(
                new AiServiceClient.AiServiceException("ai_service_timeout", true, 504, "timed out"));

        assertNull(reviewer.review(post("code", "Loop", "```java\nint x = 1;\n```")));
    }

    @Test
    @DisplayName("an unreadable 200 is data: ReviewValidity rejects it instead of calling it unavailable")
    void malformedResponseShouldBecomeAnInvalidVerdict() {
        when(client.review(any())).thenThrow(new AiServiceClient.AiServiceException(
                "ai_service_invalid_response", false, 502, "not json"));

        Reviewer.Verdict verdict = reviewer.review(post("code", "Loop", "```java\nint x = 1;\n```"));

        assertNotNull(verdict);
        assertFalse(ReviewValidity.isValid(verdict));
        assertEquals("unknown", verdict.severity);
        assertNull(verdict.resultJson);
    }

    @Test
    @DisplayName("an HTTP 200 with a nonsensical verdict is returned and rejected by ReviewValidity")
    void nonsenseVerdictShouldBeRejectedByTheJavaGate() {
        AiServiceClient.ReviewResponse nonsense = new AiServiceClient.ReviewResponse(
                5, "low", "EVALUATE", "none", "", false, "{}", "m",
                new AiServiceClient.Usage(1, 1), 1, false);
        when(client.review(any())).thenReturn(nonsense);

        Reviewer.Verdict verdict = reviewer.review(post("code", "Loop", "```java\nint x = 1;\n```"));

        assertNotNull(verdict);
        assertFalse(ReviewValidity.isValid(verdict));
    }

    private static AiServiceClient.ReviewResponse response(int score, String severity,
                                                           boolean approved, String resultJson) {
        return new AiServiceClient.ReviewResponse(
                score, severity,
                "The code is structured clearly and the loop bound is explicit.",
                "No security findings in this snippet.",
                "Extract the loop body into a small helper method.",
                approved, resultJson, "test-model",
                new AiServiceClient.Usage(100, 20), 12, false);
    }

    private static VibePost post(String type, String title, String content) {
        VibePost post = new VibePost();
        post.setId(42L);
        post.setPostType(type);
        post.setTitle(title);
        post.setContent(content);
        return post;
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
