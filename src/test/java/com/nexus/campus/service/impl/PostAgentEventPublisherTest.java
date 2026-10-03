package com.nexus.campus.service.impl;

import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.enums.PostStatus;
import com.nexus.campus.repository.AiReviewLogRepository;
import com.nexus.campus.repository.VibePostRepository;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Graceful degradation when the async pool rejects agent events at publish
 * time (the @Async submission runs in the publisher's thread, so a saturated
 * pool surfaces RejectedExecutionException here — see the 40k HTTP-500s
 * measured in docs/research/async-pool-loadtest.md).
 *
 * The publishing logic now lives behind the repository boundary, so these
 * tests drive {@link PostAgentEventPublisher} directly instead of the post
 * service facade.
 */
@ExtendWith(MockitoExtension.class)
class PostAgentEventPublisherTest {

    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private VibePostRepository posts;
    @Mock
    private AiReviewLogRepository reviewLogs;
    @Mock
    private LlmHealthCache llmHealthCache;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PostAgentEventPublisher publisher;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        meters = new SimpleMeterRegistry();
        publisher = new PostAgentEventPublisher(events, posts, reviewLogs, llmHealthCache,
                transactionManager, meters);
    }

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
                .when(events).publishEvent(any(AiReviewEvent.class));

        assertDoesNotThrow(() -> publisher.publishReview(post(1L), 9L));

        ArgumentCaptor<VibePost> captor = ArgumentCaptor.forClass(VibePost.class);
        verify(posts).update(captor.capture());
        assertEquals(AiReviewStatus.FAILED.getCode(), captor.getValue().getAiReviewed());
        // The rejection is invisible to every other signal — no error, no 5xx, no failed request —
        // so this counter is the only thing that makes pool saturation observable at all.
        assertEquals(1.0, meters.get("agent.pool.rejected.review").counter().count());
    }

    @Test
    @DisplayName("Rejected safety event fails closed with a pending-llm marker")
    void rejectedSafetyEventFailsClosed() {
        when(llmHealthCache.isHealthy()).thenReturn(true);
        doThrow(new RejectedExecutionException("pool full"))
                .when(events).publishEvent(any(AiSafetyCheckEvent.class));

        assertDoesNotThrow(() -> publisher.publishSafety(post(2L), 9L));

        // mirrors the listener's LLM-outage behavior: PENDING_REVIEW + pending-llm log
        verify(posts).updateStatus(2L, PostStatus.PENDING_REVIEW.getCode());
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(captor.capture());
        assertEquals("safety-check-agent", captor.getValue().getReviewer());
        assertEquals("pending-llm", captor.getValue().getSeverity());
        assertEquals(1.0, meters.get("agent.pool.rejected.safety").counter().count());
        assertEquals(1.0, meters.get("agent.fail.closed").counter().count());
    }

    @Test
    @DisplayName("Unhealthy LLM at enqueue fails closed before publishing: post never sits public unchecked")
    void unhealthyLlmAtEnqueueFailsClosedWithoutPublishing() {
        when(llmHealthCache.isHealthy()).thenReturn(false);

        assertDoesNotThrow(() -> publisher.publishSafety(post(3L), 9L));

        // ADR-0004: during an outage posts do not appear publicly — the event
        // must not even enter the pipeline; the post lands in the audit queue.
        verify(events, never()).publishEvent(any(AiSafetyCheckEvent.class));
        verify(posts).updateStatus(3L, PostStatus.PENDING_REVIEW.getCode());
        ArgumentCaptor<AiReviewLog> captor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(captor.capture());
        assertEquals("pending-llm", captor.getValue().getSeverity());
        assertEquals(1.0, meters.get("agent.fail.closed").counter().count());
    }

    @Test
    @DisplayName("The pipeline counters exist at zero before anything has gone wrong")
    void pipelineCountersExistAtZero() {
        // The alert rules treat absent series as a fault, which is only sound because these are
        // registered in the constructor rather than created at the first rejection. If this test
        // ever needs an event to make a series appear, the rule's no-data policy has to change.
        for (String series : List.of("agent.pool.rejected.review", "agent.pool.rejected.safety",
                "agent.fail.closed")) {
            assertNotNull(meters.find(series).counter(), series + " is not registered at zero");
            assertEquals(0.0, meters.get(series).counter().count(), series);
        }
    }

    @Test
    @DisplayName("Inside a transaction the dispatch waits for the commit instead of running inline")
    void dispatchIsDeferredUntilCommit() {
        when(llmHealthCache.isHealthy()).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            publisher.publishSafety(post(4L), 9L);

            // Publishing here would let the listener read a post nothing else can see yet, and
            // then clear the REVIEWING marker the pending transaction is about to commit.
            verify(events, never()).publishEvent(any(AiSafetyCheckEvent.class));
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(events).publishEvent(any(AiSafetyCheckEvent.class));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
