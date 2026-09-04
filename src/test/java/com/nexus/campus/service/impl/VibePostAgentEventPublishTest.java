package com.nexus.campus.service.impl;

import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.agent.AiSafetyCheckEvent;
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
import static org.mockito.Mockito.verify;

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
}
