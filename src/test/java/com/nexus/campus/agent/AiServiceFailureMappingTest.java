package com.nexus.campus.agent;

import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.repository.AiReviewLogRepository;
import com.nexus.campus.repository.VibeCommentRepository;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.service.SysMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Acceptance #4/#5 at the application seam: when the AI service is down the
 * existing FAILED + "unavailable" path must run, and a later success must
 * record the verdict normally. No new failure handling is introduced by the
 * extraction.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceFailureMappingTest {

    @Mock
    private VibePostRepository posts;
    @Mock
    private AiReviewLogRepository reviewLogs;
    @Mock
    private VibeCommentRepository comments;
    @Mock
    private SysMessageService sysMessageService;
    @Mock
    private AiServiceClient client;

    private AiReviewService reviewService;
    private ServiceReviewer reviewer;

    @BeforeEach
    void setUp() {
        CampusAiProperties properties = new CampusAiProperties();
        properties.getReview().setMaxAttempts(5);
        reviewer = new ServiceReviewer(client, properties);

        reviewService = new AiReviewService();
        ReflectionTestUtils.setField(reviewService, "posts", posts);
        ReflectionTestUtils.setField(reviewService, "reviewLogs", reviewLogs);
        ReflectionTestUtils.setField(reviewService, "comments", comments);
        ReflectionTestUtils.setField(reviewService, "sysMessageService", sysMessageService);
        ReflectionTestUtils.setField(reviewService, "aiProperties", properties);
    }

    @Test
    @DisplayName("AI service stopped: FAILED, an unavailable log row, and the existing notice")
    void stoppedServiceShouldUseTheExistingFailedPath() {
        when(client.review(any())).thenThrow(new AiServiceClient.AiServiceException(
                "ai_service_unavailable", true, 502, "connection refused"));

        reviewService.reviewPost(post(), reviewer, 10L, false, 1);

        ArgumentCaptor<AiReviewLog> log = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(log.capture());
        assertEquals("ai-service", log.getValue().getReviewer());
        assertEquals("unavailable", log.getValue().getSeverity());
        assertNull(log.getValue().getResultJson());
        verify(posts).update(org.mockito.ArgumentMatchers.argThat(
                update -> update.getAiReviewed() == AiReviewStatus.FAILED.getCode()));
        verify(sysMessageService).sendMessage(eq(0L), eq(10L), contains("暂时失败"), eq(3));
    }

    @Test
    @DisplayName("AI service returns: the same post is reviewed, scored and commented on")
    void recoveredServiceShouldCompleteTheReview() {
        when(client.review(any())).thenReturn(new AiServiceClient.ReviewResponse(
                8, "low",
                "The code is readable and the control flow is easy to follow.",
                "No security concerns were found in this snippet.",
                "Extract the loop body into a helper and add a boundary test.",
                true, "{\"score\":8}", "test-model",
                new AiServiceClient.Usage(100, 20), 10, false));

        reviewService.reviewPost(post(), reviewer, 10L, true, 2);

        verify(reviewLogs).insert(org.mockito.ArgumentMatchers.argThat(
                row -> "low".equals(row.getSeverity()) && row.getIsApproved() == 1));
        verify(posts).update(org.mockito.ArgumentMatchers.argThat(
                update -> update.getAiReviewed() == AiReviewStatus.REVIEWED.getCode()
                        && update.getAiReviewScore() == 8));
        verify(comments).insert(org.mockito.ArgumentMatchers.argThat(
                comment -> comment.getContent().contains("## AI Code Review")));
    }

    @Test
    @DisplayName("provider down behind the service: still one FAILED state and one log row")
    void providerDownShouldNotMultiplyTransitions() {
        when(client.review(any())).thenThrow(new AiServiceClient.AiServiceException(
                "model_unavailable", true, 502, "provider down"));

        reviewService.reviewPost(post(), reviewer, 10L, false, 1);

        verify(posts, org.mockito.Mockito.times(1)).update(any());
        verify(reviewLogs, org.mockito.Mockito.times(1)).insert(any());
    }

    @Test
    @DisplayName("an unreadable 200 is recorded as invalid (unknown), not as unavailable")
    void malformedResponseShouldBeRecordedAsInvalid() {
        when(client.review(any())).thenThrow(new AiServiceClient.AiServiceException(
                "ai_service_invalid_response", false, 502, "not json"));

        reviewService.reviewPost(post(), reviewer, 10L, false, 1);

        ArgumentCaptor<AiReviewLog> log = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(log.capture());
        assertEquals("unknown", log.getValue().getSeverity());
        assertEquals(0, log.getValue().getIsApproved());
        assertNull(log.getValue().getResultJson());
        verify(posts).update(org.mockito.ArgumentMatchers.argThat(
                update -> update.getAiReviewed() == AiReviewStatus.FAILED.getCode()
                        && update.getAiReviewScore() == null));
    }

    private static VibePost post() {
        VibePost post = new VibePost();
        post.setId(42L);
        post.setTitle("A post");
        post.setPostType("code");
        post.setContent("```java\nint x = 1;\n```");
        return post;
    }
}
