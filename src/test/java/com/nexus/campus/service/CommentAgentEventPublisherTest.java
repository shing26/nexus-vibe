package com.nexus.campus.service;

import com.nexus.campus.agent.AgentMentionEvent;
import com.nexus.campus.agent.AiCommentSafetyCheckEvent;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.service.impl.AgentReplyComments;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the comment safety dispatch and the mention trigger. The
 * safety path's rule (ADR-0017): nothing here ever hides or holds a comment —
 * the worst outcome is a pending-llm marker that reconciliation picks up. The
 * mention path's rule (ADR-0018): a summon is never dropped silently — the
 * worst outcome is a visible placeholder that reconciliation replaces.
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
    private AgentReplyComments agentReplyComments;
    @Mock
    private PlatformTransactionManager transactionManager;

    private CommentAgentEventPublisher publisher;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        publisher = new CommentAgentEventPublisher(events, llmHealthCache, reviewLogs,
                new CampusAiProperties(), agentReplyComments, transactionManager, meters);
    }

    private VibeComment comment() {
        VibeComment comment = new VibeComment();
        comment.setId(7L);
        comment.setPostId(42L);
        comment.setContent("hello");
        comment.setUserId(10L);
        return comment;
    }

    private VibeComment mentionComment() {
        VibeComment comment = comment();
        comment.setContent("@ai-agent 请评审这段代码的边界条件");
        return comment;
    }

    // ── safety dispatch (item 1) ─────────────────────────────────────────

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

    // ── mention trigger (item 3) ─────────────────────────────────────────

    @Test
    @DisplayName("A mention dispatches the reply event; a plain comment dispatches nothing")
    void mentionShouldDispatchAndPlainCommentShouldNot() {
        when(llmHealthCache.isHealthy()).thenReturn(true);

        publisher.publishMentionReply(mentionComment(), 10L);
        publisher.publishMentionReply(comment(), 10L);

        ArgumentCaptor<AgentMentionEvent> captor = ArgumentCaptor.forClass(AgentMentionEvent.class);
        verify(events).publishEvent(captor.capture());
        assertEquals(7L, captor.getValue().getCommentId());
        assertEquals(10L, captor.getValue().getAuthorId());
        verifyNoInteractions(agentReplyComments);
    }

    @Test
    @DisplayName("Two mentions inside the cooldown: the second summons nothing")
    void cooldownShouldSuppressSecondMention() {
        when(llmHealthCache.isHealthy()).thenReturn(true);

        publisher.publishMentionReply(mentionComment(), 10L);
        VibeComment second = mentionComment();
        second.setId(8L);
        publisher.publishMentionReply(second, 10L);

        ArgumentCaptor<AgentMentionEvent> captor = ArgumentCaptor.forClass(AgentMentionEvent.class);
        verify(events, org.mockito.Mockito.times(1)).publishEvent(captor.capture());
        assertEquals(7L, captor.getValue().getCommentId());
        assertEquals(1.0, meters.get("agent.mention.suppressed.cooldown").counter().count());
        verifyNoInteractions(agentReplyComments);
    }

    @Test
    @DisplayName("The cooldown is per user per post: another user mentions freely")
    void cooldownShouldBePerUserPerPost() {
        when(llmHealthCache.isHealthy()).thenReturn(true);

        publisher.publishMentionReply(mentionComment(), 10L);
        VibeComment otherUser = mentionComment();
        otherUser.setId(9L);
        publisher.publishMentionReply(otherUser, 11L);

        ArgumentCaptor<AgentMentionEvent> captor = ArgumentCaptor.forClass(AgentMentionEvent.class);
        verify(events, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertEquals(9L, captor.getAllValues().get(1).getCommentId());
    }

    @Test
    @DisplayName("Unhealthy LLM at mention enqueue writes the placeholder directly")
    void unhealthyMentionShouldWritePlaceholder() {
        when(llmHealthCache.isHealthy()).thenReturn(false);

        publisher.publishMentionReply(mentionComment(), 10L);

        verify(events, never()).publishEvent(any());
        verify(agentReplyComments).insertPlaceholder(eq(42L), eq(7L), eq("LLM unhealthy at enqueue"));
        assertEquals(1.0, meters.get("agent.mention.enqueue.unhealthy").counter().count());
    }

    @Test
    @DisplayName("A saturated agent pool answers the mention with a placeholder, never silence")
    void saturatedPoolShouldWriteMentionPlaceholder() {
        when(llmHealthCache.isHealthy()).thenReturn(true);
        doThrow(new RejectedExecutionException("pool full"))
                .when(events).publishEvent(any(AgentMentionEvent.class));

        publisher.publishMentionReply(mentionComment(), 10L);

        verify(agentReplyComments).insertPlaceholder(eq(42L), eq(7L), eq("agent pool saturated"));
        assertEquals(1.0, meters.get("agent.mention.pool.rejected").counter().count());
    }

    @Test
    @DisplayName("An agent-authored comment summons nothing, however it is worded")
    void agentAuthorShouldNeverSummon() {
        publisher.publishMentionReply(mentionComment(), 999L);

        verifyNoInteractions(events, agentReplyComments);
        assertEquals(0.0, meters.get("agent.mention.suppressed.cooldown").counter().count());
    }
}
