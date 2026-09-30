package com.nexus.campus.agent;

import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.config.CampusAiProperties;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Listener-level lease behavior: skip when the claim is lost, terminal
 * notification exactly once when the final attempt fails.
 */
@ExtendWith(MockitoExtension.class)
class AiReviewEventListenerTest {

    @Mock
    private AiReviewService aiReviewService;
    @Mock
    private ReviewPolicy reviewPolicy;
    @Mock
    private VibePostMapper vibePostMapper;
    @Mock
    private SysMessageService sysMessageService;

    @InjectMocks
    private AiReviewEventListener listener;

    @BeforeEach
    void enable() {
        CampusAiProperties aiProperties = new CampusAiProperties();
        aiProperties.getReview().setEnabled(true);
        aiProperties.getReview().setLeaseSeconds(30L);
        aiProperties.getReview().setMaxAttempts(5);
        aiProperties.getReview().setOwnerId("test-node");
        ReflectionTestUtils.setField(listener, "aiProperties", aiProperties);
        VibePost post = new VibePost();
        post.setId(1L);
        post.setStatus(1);
        post.setPostType("post");
        post.setContent("```java\nint x = 1;\n```");
        when(vibePostMapper.selectById(anyLong())).thenReturn(post);
        lenient().when(reviewPolicy.shouldReview(any(VibePost.class))).thenReturn(true);
    }

    private AiReviewEvent event(long postId, long authorId) {
        return new AiReviewEvent(this, postId, "Title " + postId,
                "```java\nint x = 1;\n```", authorId, true);
    }

    @Test
    @DisplayName("Claim lost (lease held elsewhere) -> review skipped, no LLM call")
    void claimLostSkipsReview() {
        when(vibePostMapper.tryClaimReview(anyLong(), any(), anyString(), anyInt())).thenReturn(0);

        listener.handleAiReviewEvent(event(1L, 9L));

        verify(aiReviewService, never()).reviewPost(anyLong(), anyString(), anyString(), any(), anyBoolean());
        verify(vibePostMapper, never()).updateById(any(VibePost.class));
    }

    @Test
    @DisplayName("Claim won -> post marked REVIEWING, review runs")
    void claimWonRunsReview() {
        when(vibePostMapper.tryClaimReview(anyLong(), any(), anyString(), anyInt())).thenReturn(1);
        when(vibePostMapper.selectReviewAttempts(1L)).thenReturn(1);

        listener.handleAiReviewEvent(event(1L, 9L));

        verify(aiReviewService).reviewPost(eq(1L), eq("Title 1"), anyString(), eq(9L), eq(true));
        ArgumentCaptor<VibePost> captor = ArgumentCaptor.forClass(VibePost.class);
        verify(vibePostMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getAiReviewed()); // REVIEWING
    }

    @Test
    @DisplayName("Ineligible post -> no claim, stale REVIEWING marker cleared")
    void ineligiblePostClearsStaleMarker() {
        when(vibePostMapper.selectById(1L)).thenReturn(null);

        listener.handleAiReviewEvent(event(1L, 9L));

        verify(vibePostMapper, never()).tryClaimReview(anyLong(), any(), anyString(), anyInt());
        verify(vibePostMapper).clearReviewingIfInState(1L, 0);
        verify(aiReviewService, never()).reviewPost(anyLong(), anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Final attempt failure sends the terminal notification exactly once")
    void finalAttemptFailureNotifiesOnce() {
        when(vibePostMapper.tryClaimReview(anyLong(), any(), anyString(), anyInt())).thenReturn(1);
        when(vibePostMapper.selectReviewAttempts(2L)).thenReturn(5);
        doThrow(new RuntimeException("LLM down")).when(aiReviewService)
                .reviewPost(anyLong(), anyString(), anyString(), any(), anyBoolean());

        listener.handleAiReviewEvent(event(2L, 9L));

        // marked FAILED (markReviewing also calls updateById, so filter by state)
        ArgumentCaptor<VibePost> captor = ArgumentCaptor.forClass(VibePost.class);
        verify(vibePostMapper, times(2)).updateById(captor.capture());
        assertEquals(3, captor.getAllValues().get(1).getAiReviewed());
        // terminal notification, exactly one
        verify(sysMessageService, times(1)).sendMessage(anyLong(), eq(9L), contains("停止自动重试"), eq(SysMessage.TYPE_SYSTEM));
    }

    @Test
    @DisplayName("Mid-budget failure does not send the terminal notification")
    void midBudgetFailureStaysSilent() {
        when(vibePostMapper.tryClaimReview(anyLong(), any(), anyString(), anyInt())).thenReturn(1);
        when(vibePostMapper.selectReviewAttempts(3L)).thenReturn(2);
        doThrow(new RuntimeException("LLM down")).when(aiReviewService)
                .reviewPost(anyLong(), anyString(), anyString(), any(), anyBoolean());

        listener.handleAiReviewEvent(event(3L, 9L));

        verify(sysMessageService, never()).sendMessage(anyLong(), anyLong(), anyString(), anyInt());
    }
}
