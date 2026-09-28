package com.nexus.campus.task;

import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.PostRankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Like reconciliation takes the union of Redis and {@code vibe_post_like}.
 * It may add members or repair the denormalised count, but it must never
 * delete a membership row. The count follows the union: when both sources
 * agree a post is unliked, the count converges to zero (a real unlike-to-zero,
 * not a lost set — see {@code docs/tickets/like-count-convergence.md}).
 */
@ExtendWith(MockitoExtension.class)
class DriftReconcileTaskTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private VibePostMapper vibePostMapper;
    @Mock
    private PostRankingService postRankingService;

    @InjectMocks
    private DriftReconcileTask task;

    private VibePost post;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(task, "driftEnabled", true);
        ReflectionTestUtils.setField(task, "sampleSize", 200);

        post = new VibePost();
        post.setId(7L);
        post.setLikeCount(0);
        lenient().when(vibePostMapper.selectUserIdsByPostId(7L)).thenReturn(List.of());
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    @DisplayName("A Redis-only set is backfilled into the durable membership table")
    void redisOnlyMembersBackfillDatabase() {
        when(setOps.size("post:like:7")).thenReturn(2L);
        when(setOps.members("post:like:7")).thenReturn(Set.of("1", "2"));

        boolean repaired = task.reconcilePost(post);

        assertTrue(repaired);
        verify(vibePostMapper).insertPostLike(7L, 1L);
        verify(vibePostMapper).insertPostLike(7L, 2L);
        verify(vibePostMapper).updateLikeCount(7L, 2);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("A lost Redis set is rebuilt from durable membership rows")
    void lostRedisSetRebuildsFromDatabase() {
        when(setOps.size("post:like:7")).thenReturn(0L);
        when(setOps.members("post:like:7")).thenReturn(Set.of());
        when(vibePostMapper.selectUserIdsByPostId(7L)).thenReturn(List.of(1L, 2L, 3L));

        boolean repaired = task.reconcilePost(post);

        assertTrue(repaired);
        verify(setOps).add("post:like:7", "1");
        verify(setOps).add("post:like:7", "2");
        verify(setOps).add("post:like:7", "3");
        verify(vibePostMapper).updateLikeCount(7L, 3);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Members present on only one side are merged without deletion")
    void divergentMembersAreMerged() {
        when(setOps.size("post:like:7")).thenReturn(1L);
        when(setOps.members("post:like:7")).thenReturn(Set.of("1"));
        when(vibePostMapper.selectUserIdsByPostId(7L)).thenReturn(List.of(2L));

        boolean repaired = task.reconcilePost(post);

        assertTrue(repaired);
        verify(vibePostMapper).insertPostLike(7L, 1L);
        verify(setOps).add("post:like:7", "2");
        verify(vibePostMapper).updateLikeCount(7L, 2);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("A matching non-empty membership with a stale count repairs only the count")
    void matchingMembersRepairDenormalizedCount() {
        post.setLikeCount(0);
        when(setOps.size("post:like:7")).thenReturn(2L);
        when(setOps.members("post:like:7")).thenReturn(Set.of("1", "2"));
        when(vibePostMapper.selectUserIdsByPostId(7L)).thenReturn(List.of(1L, 2L));

        boolean repaired = task.reconcilePost(post);

        assertTrue(repaired);
        verify(vibePostMapper).updateLikeCount(7L, 2);
    }

    @Test
    @DisplayName("Matching healthy membership and count are left alone")
    void healthyPostIsNotTouched() {
        post.setLikeCount(2);
        when(setOps.size("post:like:7")).thenReturn(2L);
        when(setOps.members("post:like:7")).thenReturn(Set.of("1", "2"));
        when(vibePostMapper.selectUserIdsByPostId(7L)).thenReturn(List.of(1L, 2L));

        assertFalse(task.reconcilePost(post));
        verify(vibePostMapper, never()).updateLikeCount(any(), anyInt());
        verify(setOps, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("Both sources empty plus a positive count resets the count to zero")
    void bothSourcesEmptyResetsCountToZero() {
        post.setLikeCount(80);
        when(setOps.size("post:like:7")).thenReturn(0L);
        when(setOps.members("post:like:7")).thenReturn(Set.of());

        assertTrue(task.reconcilePost(post));

        // the union is empty, so the denormalised count is simply wrong
        verify(vibePostMapper).updateLikeCount(7L, 0);
        // and no membership row is ever deleted
        verify(vibePostMapper, never()).deletePostLike(any(), any());
        verify(vibePostMapper, never()).insertPostLike(any(), any());
    }

    @Test
    @DisplayName("A reconciliation that changes membership triggers the ranking rebuild once")
    void repairTriggersRankingRebuild() {
        when(setOps.size("post:like:7")).thenReturn(1L);
        when(setOps.members("post:like:7")).thenReturn(Set.of("1"));
        when(vibePostMapper.selectPostWindow(0L, 200)).thenReturn(List.of(post));

        task.reconcileDrift();

        verify(postRankingService, times(1)).recalculateHotRanking();
    }
}
