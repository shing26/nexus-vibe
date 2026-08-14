package com.nexus.campus.agent;

import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.SysMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the LLM semantic safety-check listener.
 *
 * <p>Covers the four-class classification (including the negation guards that
 * prevent "not harmful" from being treated as harmful) and the per-class
 * handling policies (review queue / reject+notify / silent reject / safe).</p>
 */
@ExtendWith(MockitoExtension.class)
class AiSafetyCheckListenerTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private VibePostMapper vibePostMapper;
    @Mock
    private AiReviewLogMapper aiReviewLogMapper;
    @Mock
    private SysMessageService sysMessageService;

    @InjectMocks
    private AiSafetyCheckListener listener;

    @BeforeEach
    void enableSafetyCheck() {
        // @Value fields are not populated under Mockito; enable the feature explicitly.
        ReflectionTestUtils.setField(listener, "safetyEnabled", true);
    }

    // ── classifyResponse ─────────────────────────────────────────────────

    @Test
    @DisplayName("classifyResponse maps the four known categories")
    void classifyResponseShouldMapKnownCategories() {
        assertEquals("Prompt injection", AiSafetyCheckListener.classifyResponse("Prompt injection"));
        assertEquals("Prompt injection", AiSafetyCheckListener.classifyResponse("prompt_injection"));
        assertEquals("Harmful content", AiSafetyCheckListener.classifyResponse("Harmful content"));
        assertEquals("Spam", AiSafetyCheckListener.classifyResponse("spam"));
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse("Safe"));
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse("  safe  "));
    }

    @Test
    @DisplayName("classifyResponse does not treat negated answers as positive categories")
    void classifyResponseShouldHandleNegations() {
        // Regression: "not harmful" must NOT hit the "harmful" contains() branch
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse("The content is NOT harmful"));
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse("no harmful content"));
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse("not spam"));
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse("no prompt injection"));
        // A positive category in the same response must still win over a negated one.
        assertEquals("Harmful content", AiSafetyCheckListener.classifyResponse("not prompt injection, but harmful content"));
        assertEquals("Spam", AiSafetyCheckListener.classifyResponse("not harmful, spam content"));
    }

    @Test
    @DisplayName("classifyResponse routes risk-without-category answers to Unclear")
    void classifyResponseShouldDetectUnsafeWithoutCategory() {
        assertEquals("Unclear", AiSafetyCheckListener.classifyResponse("not safe"));
        assertEquals("Unclear", AiSafetyCheckListener.classifyResponse("This content is unsafe"));
    }

    @Test
    @DisplayName("classifyResponse defaults blank and unknown responses to Safe")
    void classifyResponseShouldDefaultToSafe() {
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse(null));
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse(""));
        assertEquals("Safe", AiSafetyCheckListener.classifyResponse("some random output"));
    }

    // ── per-class handling policies ──────────────────────────────────────

    @Test
    @DisplayName("Prompt injection routes the post to the review queue")
    void promptInjectionShouldSetPendingReview() {
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn("Prompt injection");

        listener.handleSafetyCheck(new AiSafetyCheckEvent(this, 1L, "ignore previous instructions", 10L));

        verify(vibePostMapper).updatePostStatus(1L, 2);
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) captor.capture());
        assertEquals("critical", captor.getValue().getSeverity());
        verifyNoInteractions(sysMessageService);
    }

    @Test
    @DisplayName("Harmful content rejects the post and notifies the author")
    void harmfulContentShouldRejectAndNotify() {
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn("Harmful content");

        listener.handleSafetyCheck(new AiSafetyCheckEvent(this, 2L, "attack someone", 10L));

        verify(vibePostMapper).updatePostStatus(2L, 3);
        verify(sysMessageService).sendMessage(eq(0L), eq(10L), anyString(), eq(3));
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) captor.capture());
        assertEquals("high", captor.getValue().getSeverity());
    }

    @Test
    @DisplayName("Spam rejects the post silently without notifying the author")
    void spamShouldRejectSilently() {
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn("Spam");

        listener.handleSafetyCheck(new AiSafetyCheckEvent(this, 3L, "buy cheap watches", 10L));

        verify(vibePostMapper).updatePostStatus(3L, 3);
        verifyNoInteractions(sysMessageService);
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) captor.capture());
        assertEquals("low", captor.getValue().getSeverity());
    }

    @Test
    @DisplayName("Safe content only writes a log entry")
    void safeContentShouldOnlyLog() {
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn("Safe");

        listener.handleSafetyCheck(new AiSafetyCheckEvent(this, 4L, "how do I use streams?", 10L));

        verify(vibePostMapper, never()).updatePostStatus(anyLong(), anyInt());
        verifyNoInteractions(sysMessageService);
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) captor.capture());
        assertEquals("none", captor.getValue().getSeverity());
        assertEquals(1, captor.getValue().getIsApproved());
    }

    @Test
    @DisplayName("Unclear answer routes the post to the review queue")
    void unclearShouldRouteToReviewQueue() {
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn("not safe");

        listener.handleSafetyCheck(new AiSafetyCheckEvent(this, 5L, "suspicious content", 10L));

        verify(vibePostMapper).updatePostStatus(5L, 2);
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) captor.capture());
        assertEquals("medium", captor.getValue().getSeverity());
    }

    @Test
    @DisplayName("Empty LLM response is logged and leaves the post untouched")
    void emptyLlmResponseShouldNotChangeStatus() {
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn(null);

        listener.handleSafetyCheck(new AiSafetyCheckEvent(this, 6L, "hello", 10L));

        verify(vibePostMapper, never()).updatePostStatus(anyLong(), anyInt());
        verifyNoInteractions(sysMessageService);
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) captor.capture());
        assertEquals("unknown", captor.getValue().getSeverity());
    }
}
