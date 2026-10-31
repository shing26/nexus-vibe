package com.nexus.campus.service;

import com.nexus.campus.agent.AiCommentSafetyCheckEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.service.impl.CommentAgentEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the comment safety dispatch. ADR-0017's rule on this path:
 * nothing here ever hides or holds a comment — the worst outcome is a
 * pending-llm marker that reconciliation will pick up.
 */
@ExtendWith(MockitoExtension.class)
class CommentAgentEventPublisherTest {

    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private LlmHealthCache llmHealthCache;
    @Mock
    private AiReviewLogMapper reviewLogs;
    @Mock
    private PlatformTransactionManager transactionManager;

    private CommentAgentEventPublisher publisher;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        publisher = new CommentAgentEventPublisher(events, llmHealthCache, reviewLogs,
                transactionManager, meters);
    }

    private VibeComment comment() {
        VibeComment comment = new VibeComment();
        comment.setId(7L);
        comment.setPostId(42L);
        comment.setContent("hello");
        comment.setUserId(10L);
        return comment;
    }

    @Test
    @DisplayName("Healthy LLM publishes the event after commit, no marker")
    void healthyShouldPublishEvent() {
        when(llmHealthCache.isHealthy()).thenReturn(true);

        publisher.publishSafetyCheck(comment(), 10L);

        ArgumentCaptor<AiCommentSafetyCheckEvent> captor =
                ArgumentCaptor.forClass(AiCommentSafetyCheckEvent.class);
        verify(events).publishEvent(captor.capture());
        assertEquals(7L, captor.getValue().getCommentId());
        assertEquals(42L, captor.getValue().getPostId());
        verifyNoInteractions(reviewLogs);
    }

    @Test
    @DisplayName("Unhealthy LLM at enqueue defers via a marker; the comment stays visible")
    void unhealthyShouldWriteMarkerWithoutPublishing() {
        when(llmHealthCache.isHealthy()).thenReturn(false);

        publisher.publishSafetyCheck(comment(), 10L);

        verify(events, never()).publishEvent(any());
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(captor.capture());
        assertEquals("pending-llm", captor.getValue().getSeverity());
        assertEquals("comment-safety-agent", captor.getValue().getReviewer());
        assertEquals(7L, captor.getValue().getCommentId());
        assertEquals(1.0, meters.get("agent.comment.safety.enqueue.unhealthy").counter().count());
    }

    @Test
    @DisplayName("A saturated agent pool writes a marker instead of dropping the check")
    void saturatedPoolShouldWriteMarker() {
        when(llmHealthCache.isHealthy()).thenReturn(true);
        doThrow(new RejectedExecutionException("pool full"))
                .when(events).publishEvent(any(AiCommentSafetyCheckEvent.class));

        publisher.publishSafetyCheck(comment(), 10L);

        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(captor.capture());
        assertEquals("pending-llm", captor.getValue().getSeverity());
        assertEquals(1.0, meters.get("agent.comment.safety.pool.rejected").counter().count());
    }

    @Test
    @DisplayName("A comment without an id is not dispatched anywhere")
    void unsavedCommentShouldBeIgnored() {
        VibeComment unsaved = comment();
        unsaved.setId(null);

        publisher.publishSafetyCheck(unsaved, 10L);

        verifyNoInteractions(events, reviewLogs);
        // Both counters exist from construction (registered eagerly) but nothing moved.
        assertEquals(0.0, meters.get("agent.comment.safety.enqueue.unhealthy").counter().count());
        assertEquals(0.0, meters.get("agent.comment.safety.pool.rejected").counter().count());
    }
}
