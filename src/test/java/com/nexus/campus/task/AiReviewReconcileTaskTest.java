package com.nexus.campus.task;

import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.agent.LlmClient;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.mapper.VibePostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the AI reconciliation task: stale REVIEWING/FAILED reviews
 * and pending-llm safety checks get re-triggered, but only when the LLM is
 * healthy (probed at most once per cache window).
 */
@ExtendWith(MockitoExtension.class)
class AiReviewReconcileTaskTest {

    @Mock
    private VibePostMapper vibePostMapper;
    @Mock
    private LlmClient llmClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AiReviewReconcileTask task;

    @BeforeEach
    void enableFeatures() {
        ReflectionTestUtils.setField(task, "reviewEnabled", true);
        ReflectionTestUtils.setField(task, "safetyEnabled", true);
    }

    private VibePost post(long id) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setTitle("Post " + id);
        post.setContent("```java\nint x = " + id + ";\n```");
        post.setUserId(10L);
        return post;
    }

    @Test
    @DisplayName("Skips the whole cycle when the LLM is unhealthy")
    void shouldSkipWhenLlmUnhealthy() {
        when(llmClient.isHealthy()).thenReturn(false);

        task.reconcile();

        verifyNoInteractions(vibePostMapper, eventPublisher);
    }

    @Test
    @DisplayName("Re-triggers stale REVIEWING and FAILED reviews")
    void shouldRetriggerStaleReviews() {
        when(llmClient.isHealthy()).thenReturn(true);
        when(vibePostMapper.selectStaleAiReviewPosts(eq(AiReviewStatus.REVIEWING.getCode()), any(), anyInt(), anyInt()))
                .thenReturn(List.of(post(1L)));
        when(vibePostMapper.selectStaleAiReviewPosts(eq(AiReviewStatus.FAILED.getCode()), any(), anyInt(), anyInt()))
                .thenReturn(List.of(post(2L)));
        when(vibePostMapper.selectPostsPendingSafetyRecheck(any(), anyInt()))
                .thenReturn(List.of());

        task.reconcile();

        ArgumentCaptor<AiReviewEvent> events = ArgumentCaptor.forClass(AiReviewEvent.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertEquals(1L, events.getAllValues().get(0).getPostId());
        assertEquals(2L, events.getAllValues().get(1).getPostId());
    }

    @Test
    @DisplayName("Re-runs safety checks for posts marked pending-llm")
    void shouldReRunPendingSafetyChecks() {
        when(llmClient.isHealthy()).thenReturn(true);
        when(vibePostMapper.selectStaleAiReviewPosts(anyInt(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vibePostMapper.selectPostsPendingSafetyRecheck(any(), anyInt()))
                .thenReturn(List.of(post(3L)));

        task.reconcile();

        ArgumentCaptor<AiSafetyCheckEvent> events = ArgumentCaptor.forClass(AiSafetyCheckEvent.class);
        verify(eventPublisher).publishEvent(events.capture());
        AiSafetyCheckEvent safetyEvent = events.getValue();
        assertEquals(3L, safetyEvent.getPostId());
        assertEquals(10L, safetyEvent.getAuthorId());
    }

    @Test
    @DisplayName("Caches the health probe so repeated cycles do not re-probe immediately")
    void shouldCacheHealthProbe() {
        when(llmClient.isHealthy()).thenReturn(true);
        when(vibePostMapper.selectStaleAiReviewPosts(anyInt(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vibePostMapper.selectPostsPendingSafetyRecheck(any(), anyInt()))
                .thenReturn(List.of());

        task.reconcile();
        task.reconcile();

        verify(llmClient, times(1)).isHealthy();
    }
}
