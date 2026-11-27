package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.config.CampusAiProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.SysMessageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the comment safety-check listener. The semantics diverge
 * from the post listener on purpose (ADR-0017): the comment is already
 * visible when the check runs, so every unusable outcome leaves it visible
 * with a pending-llm marker, and only harmful/spam/prompt-injection verdicts
 * change its status.
 */
@ExtendWith(MockitoExtension.class)
class AiCommentSafetyCheckListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Mock
    private SafetyClassifier safetyClassifier;
    @Mock
    private VibeCommentMapper vibeCommentMapper;
    @Mock
    private VibePostMapper vibePostMapper;
    @Mock
    private AiReviewLogMapper aiReviewLogMapper;
    @Mock
    private SysMessageService sysMessageService;

    @InjectMocks
    private AiCommentSafetyCheckListener listener;

    @BeforeAll
    static void initLambdaColumnCache() {
        // The hide/audit paths build LambdaUpdateWrappers over VibeComment, which
        // resolve columns from a MyBatis-Plus cache a Spring context normally
        // fills. Same warm-up the reconcile test needs for VibePost.
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), VibeComment.class);
    }

    @BeforeEach
    void enableSafetyCheck() {
        CampusAiProperties aiProperties = new CampusAiProperties();
        aiProperties.getSafety().setEnabled(true);
        ReflectionTestUtils.setField(listener, "aiProperties", aiProperties);
        ReflectionTestUtils.setField(listener, "meterRegistry", meters);
        listener.registerVerdictCounter();
    }

    private JsonNode classification(String value) throws Exception {
        return objectMapper.readTree(
                "{\"classification\":\"" + value + "\",\"confidence\":0.9,\"reason\":\"test reason\"}");
    }

    private AiCommentSafetyCheckEvent event(Long commentId) {
        return new AiCommentSafetyCheckEvent(this, commentId, 42L, "comment content", 10L);
    }

    @Test
    @DisplayName("Harmful comment is hidden conditionally and the author is notified")
    void harmfulCommentShouldHideAndNotify() throws Exception {
        when(safetyClassifier.classify(anyString(), any()))
                .thenReturn(classification("harmful"));
        when(vibeCommentMapper.update(any(), any())).thenReturn(1);

        listener.handleCommentSafetyCheck(event(1L));

        verify(safetyClassifier).classify(eq("comment content"),
                eq(SafetyClassifier.Surface.COMMENT));
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert(captor.capture());
        assertEquals("high", captor.getValue().getSeverity());
        assertEquals(1L, captor.getValue().getCommentId());
        assertEquals(42L, captor.getValue().getPostId());
        verify(sysMessageService).sendMessage(any(), eq(10L), contains("已被隐藏"), any());
        // Hiding changes the visible count, so it is recalculated
        verify(vibePostMapper).recalculateCommentCount(42L);
    }

    @Test
    @DisplayName("Spam comment is hidden silently without notifying the author")
    void spamCommentShouldHideSilently() throws Exception {
        when(safetyClassifier.classify(anyString(), any()))
                .thenReturn(classification("spam"));
        when(vibeCommentMapper.update(any(), any())).thenReturn(1);

        listener.handleCommentSafetyCheck(event(2L));

        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert(captor.capture());
        assertEquals("low", captor.getValue().getSeverity());
        verifyNoInteractions(sysMessageService);
    }

    @Test
    @DisplayName("Prompt injection moves the comment to the audit queue, not to hidden")
    void promptInjectionShouldQueueForAudit() throws Exception {
        when(safetyClassifier.classify(anyString(), any()))
                .thenReturn(classification("prompt_injection"));
        when(vibeCommentMapper.update(any(), any())).thenReturn(1);

        listener.handleCommentSafetyCheck(event(3L));

        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert(captor.capture());
        assertEquals("critical", captor.getValue().getSeverity());
        verify(sysMessageService).sendMessage(any(), eq(10L), contains("人工审核"), any());
    }

    @Test
    @DisplayName("Safe comment logs the verdict and touches no status")
    void safeCommentShouldOnlyLog() throws Exception {
        when(safetyClassifier.classify(anyString(), any()))
                .thenReturn(classification("safe"));

        listener.handleCommentSafetyCheck(event(4L));

        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert(captor.capture());
        assertEquals("none", captor.getValue().getSeverity());
        assertEquals(1, captor.getValue().getIsApproved());
        verify(vibeCommentMapper, never()).update(any(), any());
        verifyNoInteractions(sysMessageService);
    }

    @Test
    @DisplayName("LLM unavailable leaves the comment visible with a pending-llm marker (ADR-0017)")
    void unavailableLlmShouldMarkPendingAndStayVisible() {
        when(safetyClassifier.classify(anyString(), any()))
                .thenReturn(null);

        listener.handleCommentSafetyCheck(event(5L));

        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert(captor.capture());
        assertEquals("pending-llm", captor.getValue().getSeverity());
        assertEquals(5L, captor.getValue().getCommentId());
        // The opposite of the post path: no status change, no author notice
        verify(vibeCommentMapper, never()).update(any(), any());
        verifyNoInteractions(sysMessageService);
    }

    @Test
    @DisplayName("Unexpected exception keeps the comment visible with a pending-llm marker")
    void unexpectedExceptionShouldMarkPending() {
        when(safetyClassifier.classify(anyString(), any()))
                .thenThrow(new RuntimeException("connection reset"));

        listener.handleCommentSafetyCheck(event(6L));

        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert(captor.capture());
        assertEquals("pending-llm", captor.getValue().getSeverity());
        verify(vibeCommentMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("A late verdict never flips a comment that is no longer visible")
    void lateVerdictShouldNotTouchNonVisibleComment() throws Exception {
        when(safetyClassifier.classify(anyString(), any()))
                .thenReturn(classification("harmful"));
        // The conditional hide found nothing: the comment was deleted or audited meanwhile
        when(vibeCommentMapper.update(any(), any())).thenReturn(0);

        listener.handleCommentSafetyCheck(event(7L));

        // The verdict is still logged, but the denormalised count is not
        // recalculated — nothing visible changed.
        verify(aiReviewLogMapper).insert(any(AiReviewLog.class));
        verify(vibePostMapper, never()).recalculateCommentCount(42L);
        verify(sysMessageService).sendMessage(any(), eq(10L), contains("已被隐藏"), any());
    }

}
