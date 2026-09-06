package com.nexus.campus.service.impl;

import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.impl.VibePostServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for graceful degradation when the async pool rejects agent
 * events at publish time (the @Async submission runs in the publisher's
 * thread, so a saturated pool surfaces RejectedExecutionException here —
 * see the 40k HTTP-500s measured in docs/research/async-pool-loadtest.md).
 */
@ExtendWith(MockitoExtension.class)
class VibePostAgentEventPublishTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private VibePostMapper vibePostMapper;
    @Mock
    private AiReviewLogMapper aiReviewLogMapper;
    @Mock
    private LlmHealthCache llmHealthCache;

    @InjectMocks
    private VibePostServiceImpl service;

    private VibePost post(long id) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setTitle("Saturated pipeline post");
        post.setContent("```java\nint x = 1;\n```");
        post.setUserId(9L);
        return post;
    }

    @Test
    @DisplayName("Rejected review event marks the post FAILED instead of throwing")
    void rejectedReviewEventMarksFailed() {
        doThrow(new RejectedExecutionException("pool full"))
                .when(eventPublisher).publishEvent(any(AiReviewEvent.class));

        assertDoesNotThrow(() -> service.publishReviewEventSafely(post(1L), 9L));

        ArgumentCaptor<VibePost> captor = ArgumentCaptor.forClass(VibePost.class);
        verify(vibePostMapper).updateById(captor.capture());
        assertEquals(AiReviewStatus.FAILED.getCode(), captor.getValue().getAiReviewed());
    }

    @Test
    @DisplayName("Rejected safety event fails closed with a pending-llm marker")
    void rejectedSafetyEventFailsClosed() {
        when(llmHealthCache.isHealthy()).thenReturn(true);
        doThrow(new RejectedExecutionException("pool full"))
                .when(eventPublisher).publishEvent(any(AiSafetyCheckEvent.class));

        assertDoesNotThrow(() -> service.publishSafetyEventSafely(post(2L), 9L));

        // mirrors the listener's LLM-outage behavior: PENDING_REVIEW + pending-llm log
        verify(vibePostMapper).updatePostStatus(2L, 2);
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert(captor.capture());
        assertEquals("safety-check-agent", captor.getValue().getReviewer());
        assertEquals("pending-llm", captor.getValue().getSeverity());
    }

    @Test
    @DisplayName("Unhealthy LLM at enqueue fails closed before publishing: post never sits public unchecked")
    void unhealthyLlmAtEnqueueFailsClosedWithoutPublishing() {
        when(llmHealthCache.isHealthy()).thenReturn(false);

        assertDoesNotThrow(() -> service.publishSafetyEventSafely(post(3L), 9L));

        // ADR-0004: during an outage posts do not appear publicly — the event
        // must not even enter the pipeline; the post lands in the audit queue.
        verify(eventPublisher, never()).publishEvent(any(AiSafetyCheckEvent.class));
        verify(vibePostMapper).updatePostStatus(3L, 2);
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert(captor.capture());
        assertEquals("pending-llm", captor.getValue().getSeverity());
    }
}
