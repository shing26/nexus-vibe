package com.nexus.campus.task;

import com.nexus.campus.mapper.VibePostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LikeSyncTaskTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private VibePostMapper vibePostMapper;

    @InjectMocks
    private LikeSyncTask likeSyncTask;

    private final Long postId1 = 10L;
    private final Long postId2 = 20L;
    private final String dirtyKey = "post:like:dirty";
    private final String likeKey1 = "post:like:10";
    private final String likeKey2 = "post:like:20";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    // -- No dirty posts --

    @Test
    @DisplayName("syncLikes() should skip when the SPOP batch is empty")
    void syncLikesNoDirtyPosts() {
        when(setOperations.pop(dirtyKey, LikeSyncTask.BATCH_SIZE)).thenReturn(List.of());

        likeSyncTask.syncLikes();

        verify(vibePostMapper, never()).updateLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("syncLikes() should skip when the SPOP batch is null")
    void syncLikesDirtySetNull() {
        when(setOperations.pop(dirtyKey, LikeSyncTask.BATCH_SIZE)).thenReturn(null);

        likeSyncTask.syncLikes();

        verify(vibePostMapper, never()).updateLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("syncLikes() should skip when Redis is unavailable")
    void syncLikesRedisUnavailable() {
        LikeSyncTask task = new LikeSyncTask();
        ReflectionTestUtils.setField(task, "vibePostMapper", vibePostMapper);
        // redisTemplate is null

        task.syncLikes();

        verify(vibePostMapper, never()).updateLikeCount(anyLong(), anyInt());
    }

    // -- Happy path --

    @Test
    @DisplayName("syncLikes() should sync the popped batch to MySQL (no SREM: pop removed them)")
    void syncLikesHappyPath() {
        when(setOperations.pop(dirtyKey, LikeSyncTask.BATCH_SIZE)).thenReturn(List.of("10", "20"));
        when(setOperations.size(likeKey1)).thenReturn(5L);
        when(setOperations.size(likeKey2)).thenReturn(3L);

        likeSyncTask.syncLikes();

        verify(setOperations).pop(dirtyKey, LikeSyncTask.BATCH_SIZE);
        verify(vibePostMapper).updateLikeCount(postId1, 5);
        verify(vibePostMapper).updateLikeCount(postId2, 3);
        // SPOP already dequeued — no explicit removal
        verify(setOperations, never()).remove(eq(dirtyKey), anyString());
    }

    @Test
    @DisplayName("syncLikes() should handle null SCARD gracefully")
    void syncLikesNullScard() {
        when(setOperations.pop(dirtyKey, LikeSyncTask.BATCH_SIZE)).thenReturn(List.of("10"));
        when(setOperations.size(likeKey1)).thenReturn(null);

        likeSyncTask.syncLikes();

        verify(vibePostMapper, never()).updateLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("syncLikes() should re-queue a failed post and continue the batch")
    void syncLikesPartialFailure() {
        when(setOperations.pop(dirtyKey, LikeSyncTask.BATCH_SIZE)).thenReturn(List.of("10", "20"));
        when(setOperations.size(likeKey1)).thenThrow(new RuntimeException("Redis error"));
        when(setOperations.size(likeKey2)).thenReturn(3L);

        likeSyncTask.syncLikes();

        verify(vibePostMapper).updateLikeCount(postId2, 3);
        verify(vibePostMapper, never()).updateLikeCount(eq(postId1), anyInt());
        // the failed post goes back into the dirty set for the next cycle
        verify(setOperations).add(dirtyKey, "10");
    }

    @Test
    @DisplayName("syncLikes() should re-queue a MySQL write failure too")
    void syncLikesMysqlFailureRequeues() {
        when(setOperations.pop(dirtyKey, LikeSyncTask.BATCH_SIZE)).thenReturn(List.of("10"));
        when(setOperations.size(likeKey1)).thenReturn(5L);
        when(vibePostMapper.updateLikeCount(postId1, 5))
                .thenThrow(new RuntimeException("deadlock detected"));

        likeSyncTask.syncLikes();

        verify(setOperations).add(dirtyKey, "10");
    }
}
