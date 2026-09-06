package com.nexus.campus.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.agent.LlmClient;
import com.nexus.campus.agent.LlmHealthCache;
import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private SysMessageService sysMessageService;

    @InjectMocks
    private AiReviewReconcileTask task;

    @BeforeEach
    void enableFeatures() {
        ReflectionTestUtils.setField(task, "reviewEnabled", true);
        ReflectionTestUtils.setField(task, "safetyEnabled", true);
        ReflectionTestUtils.setField(task, "maxAttempts", 5);
        // The task now consumes health through the shared cache; wire it to
        // the mocked LlmClient so probe caching is exercised for real.
        ReflectionTestUtils.setField(task, "llmHealthCache", new LlmHealthCache(llmClient));
        // Every reconcile cycle runs the budget-exhausted sweep first; tests
        // that exercise the sweep override this default with their own stub.
        lenient().when(vibePostMapper.selectReviewingBudgetExhausted(anyInt(), anyInt())).thenReturn(List.of());
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
    @DisplayName("Skips the whole retry cycle when the LLM is unhealthy")
    void shouldSkipWhenLlmUnhealthy() {
        when(llmClient.isHealthy()).thenReturn(false);

        task.reconcile();

        // the sweep still ran (before the gate) but nothing is re-triggered
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Budget-exhausted REVIEWING posts retire to FAILED and notify once")
    void shouldSweepBudgetExhaustedReviews() {
        when(llmClient.isHealthy()).thenReturn(false); // sweep must run even on outage
        VibePost stuck = post(5L);
        when(vibePostMapper.selectReviewingBudgetExhausted(5, 10)).thenReturn(List.of(stuck));
        when(vibePostMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        task.reconcile();

        verify(vibePostMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(sysMessageService).sendMessage(eq(SysMessage.FROM_SYSTEM), eq(10L),
                contains("停止自动重试"), eq(SysMessage.TYPE_SYSTEM));
    }

    @Test
    @DisplayName("Sweep skips the notification when another instance already retired the post")
    void shouldNotNotifyWhenRetirementLostTheRace() {
        when(llmClient.isHealthy()).thenReturn(false);
        when(vibePostMapper.selectReviewingBudgetExhausted(5, 10)).thenReturn(List.of(post(6L)));
        when(vibePostMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        task.reconcile();

        verifyNoInteractions(sysMessageService);
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
