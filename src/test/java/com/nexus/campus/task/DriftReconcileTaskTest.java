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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drift reconciliation: only Redis-LOSS-shaped gaps (DB >> Redis) trigger a
 * rebuild from vibe_post_like; normal write-behind drift (Redis >= DB) is
 * left alone, and the hot-ranking ZSET rebuild piggybacks on the repair.
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
        ReflectionTestUtils.setField(task, "driftRatio", 0.5);
        ReflectionTestUtils.setField(task, "driftAbs", 100L);
        ReflectionTestUtils.setField(task, "sampleSize", 200);

        post = new VibePost();
        post.setId(7L);
        post.setLikeCount(500);
        lenient().when(vibePostMapper.selectUserIdsByPostId(7L)).thenReturn(List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("Redis-loss shape (DB=500, Redis=1) triggers a rebuild from like rows")
    void lossShapeTriggersRebuild() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("post:like:7")).thenReturn(1L);
        when(setOps.add(anyString(), anyString())).thenReturn(1L);

        boolean repaired = task.repairIfLost(post);

        assertTrue(repaired);
        // set rebuilt with the durable like rows
        verify(redisTemplate).delete("post:like:7");
        verify(setOps).add("post:like:7", "1");
        verify(setOps).add("post:like:7", "2");
        verify(setOps).add("post:like:7", "3");
        // MySQL overwritten with the durable count (3 rows), not the stale 1
        verify(vibePostMapper).updateLikeCount(7L, 3);
    }

    @Test
    @DisplayName("Repair cycle also rebuilds the hot-ranking ZSET once")
    void repairTriggersRankingRebuild() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size(anyString())).thenReturn(1L);
        when(setOps.add(anyString(), anyString())).thenReturn(1L);
        when(vibePostMapper.selectPostWindow(0L, 200)).thenReturn(List.of(post));
        lenient().when(vibePostMapper.selectPostWindow(7L, 200)).thenReturn(List.of());

        task.reconcileDrift();

        verify(postRankingService, times(1)).recalculateHotRanking();
    }

    @Test
    @DisplayName("Normal write-behind drift (Redis ahead of DB) is left alone")
    void normalDriftNotTouched() {
        post.setLikeCount(500);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("post:like:7")).thenReturn(505L); // Redis ahead: normal

        assertFalse(task.repairIfLost(post));
        verify(redisTemplate, never()).delete(anyString());
        verify(vibePostMapper, never()).updateLikeCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Small counts below the absolute threshold are not repaired")
    void smallCountsIgnored() {
        post.setLikeCount(20);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("post:like:7")).thenReturn(1L);

        assertFalse(task.repairIfLost(post));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Healthy posts (no drift) skip the rebuild path")
    void healthyPostSkipped() {
        post.setLikeCount(500);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("post:like:7")).thenReturn(500L);

        assertFalse(task.repairIfLost(post));
        verify(redisTemplate, never()).delete(anyString());
        verify(vibePostMapper, never()).updateLikeCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
