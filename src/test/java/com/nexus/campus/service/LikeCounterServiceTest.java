package com.nexus.campus.service;

import com.nexus.campus.entity.VibePost;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LikeCounterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private VibePostMapper vibePostMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private DefaultRedisScript<Long> likeToggleScript;

    @Mock
    private PostRankingService postRankingService;

    @InjectMocks
    private LikeCounterService likeCounterService;

    private final Long postId = 42L;
    private final Long userId = 100L;
    private final String likeKey = "post:like:42";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    // ------------------------------------------------------------
    // Redis unavailable -> fallback to MySQL
    // ------------------------------------------------------------

    @Test
    @DisplayName("likePost() should fall back to MySQL when Redis is unavailable")
    void likePostRedisUnavailable() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", false);
        VibePost post = new VibePost();
        post.setId(postId);
        post.setLikeCount(10);
        when(vibePostMapper.insertPostLike(postId, userId)).thenReturn(1);
        when(vibePostMapper.selectById(postId)).thenReturn(post);

        long count = likeCounterService.likePost(postId, userId);

        assertEquals(10, count);
        verify(vibePostMapper).insertPostLike(postId, userId);
        verify(vibePostMapper).updateLikeCountDelta(postId, 1);
    }

    @Test
    @DisplayName("unlikePost() should fall back to MySQL when Redis is unavailable")
    void unlikePostRedisUnavailable() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", false);
        VibePost post = new VibePost();
        post.setId(postId);
        post.setLikeCount(9);
        when(vibePostMapper.deletePostLike(postId, userId)).thenReturn(1);
        when(vibePostMapper.selectById(postId)).thenReturn(post);

        long count = likeCounterService.unlikePost(postId, userId);

        assertEquals(9, count);
        verify(vibePostMapper).deletePostLike(postId, userId);
        verify(vibePostMapper).updateLikeCountDelta(postId, -1);
    }

    @Test
    @DisplayName("isLiked() should return false when Redis is unavailable")
    void isLikedRedisUnavailable() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", false);
        when(vibePostMapper.countPostLike(postId, userId)).thenReturn(0);

        boolean result = likeCounterService.isLiked(postId, userId);

        assertFalse(result);
    }

    @Test
    @DisplayName("isLiked() should read the like table when Redis is unavailable")
    void isLikedFallbackToLikeTable() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", false);
        when(vibePostMapper.countPostLike(postId, userId)).thenReturn(1);

        boolean result = likeCounterService.isLiked(postId, userId);

        assertTrue(result);
    }

    @Test
    @DisplayName("getLikeCount() should fall back to MySQL when Redis is unavailable")
    void getLikeCountRedisUnavailable() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", false);
        VibePost post = new VibePost();
        post.setId(postId);
        post.setLikeCount(25);
        when(vibePostMapper.selectById(postId)).thenReturn(post);

        long count = likeCounterService.getLikeCount(postId);

        assertEquals(25, count);
    }

    @Test
    @DisplayName("getLikeCount() should return 0 when post not found and Redis unavailable")
    void getLikeCountPostNotFound() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", false);
        when(vibePostMapper.selectById(postId)).thenReturn(null);

        long count = likeCounterService.getLikeCount(postId);

        assertEquals(0, count);
    }

    // ------------------------------------------------------------
    // Redis available
    // ------------------------------------------------------------

    @Test
    @DisplayName("likePost() should use Redis SADD and return the set size")
    void likePostViaRedis() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", true);
        when(stringRedisTemplate.execute(same(likeToggleScript), anyList(), anyString(), anyString(), anyString())).thenReturn(5L);

        long count = likeCounterService.likePost(postId, userId);

        assertEquals(5L, count);
    }

    @Test
    @DisplayName("likePost() should be idempotent when user already liked")
    void likePostIdempotent() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", true);
        when(stringRedisTemplate.execute(same(likeToggleScript), anyList(), anyString(), anyString(), anyString())).thenReturn(3L);

        long count = likeCounterService.likePost(postId, userId);

        assertEquals(3L, count);
    }

    @Test
    @DisplayName("unlikePost() should remove user from Redis set and return updated count")
    void unlikePostViaRedis() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", true);
        when(stringRedisTemplate.execute(same(likeToggleScript), anyList(), anyString(), anyString(), anyString())).thenReturn(4L);

        long count = likeCounterService.unlikePost(postId, userId);

        assertEquals(4L, count);
    }

    @Test
    @DisplayName("unlikePost() should not mark dirty when user was not in set")
    void unlikePostNoEffect() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", true);
        when(stringRedisTemplate.execute(same(likeToggleScript), anyList(), anyString(), anyString(), anyString())).thenReturn(2L);

        long count = likeCounterService.unlikePost(postId, userId);

        assertEquals(2L, count);
    }

    @Test
    @DisplayName("isLiked() should check Redis SISMEMBER")
    void isLikedViaRedis() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", true);

        boolean result = likeCounterService.isLiked(postId, userId);

        assertFalse(result);
    }

    @Test
    @DisplayName("isLiked() should return false when user has not liked")
    void isNotLikedViaRedis() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", true);

        boolean result = likeCounterService.isLiked(postId, userId);

        assertFalse(result);
    }

    @Test
    @DisplayName("getLikeCount() should read from Redis SCARD")
    void getLikeCountViaRedis() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", true);
        when(stringRedisTemplate.opsForSet().size(likeKey)).thenReturn(7L);

        long count = likeCounterService.getLikeCount(postId);

        assertEquals(7L, count);
    }

    @Test
    @DisplayName("getLikeCount() should return 0 when Redis set does not exist")
    void getLikeCountRedisEmpty() {
        ReflectionTestUtils.setField(likeCounterService, "redisAvailable", true);
        when(stringRedisTemplate.opsForSet().size(likeKey)).thenReturn(null);

        long count = likeCounterService.getLikeCount(postId);

        assertEquals(0, count);
    }
}
