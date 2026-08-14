package com.nexus.campus.service;

import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Arrays;

/**
 * Redis-backed like counter with atomic Lua toggle and scheduled delta flush.
 *
 * <p>Uses a Lua script ({@code lua/like_toggle.lua}) to atomically toggle
 * a user's like on a post, updating the membership Set, delta Hash, dirty
 * tracker Set, and ranking ZSet in a single round-trip. A separate
 * {@code LikeSyncTask} periodically flushes accumulated deltas to MySQL.</p>
 *
 * <p>If Redis is unavailable the service degrades gracefully and falls back
 * to direct MySQL writes.</p>
 */
@Service
public class LikeCounterService {

    private static final Logger log = LoggerFactory.getLogger(LikeCounterService.class);

    private static final String LIKE_SET_PREFIX   = "post:like:";
    private static final String LIKE_DELTA_PREFIX = "post:like:delta:";
    static final String DIRTY_SET_KEY     = "post:like:dirty";
    static final String RANKING_KEY       = "post:ranking:likes";
    private static final String DEFAULT_WEIGHT = "3";

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private DefaultRedisScript<Long> likeToggleScript;

    @Autowired
    private VibePostMapper vibePostMapper;

    boolean redisAvailable;

    @PostConstruct
    void init() {
        redisAvailable = (stringRedisTemplate != null && likeToggleScript != null);
        if (redisAvailable) {
            try {
                RedisConnection conn = stringRedisTemplate.getConnectionFactory().getConnection();
                conn.ping();
                conn.close();
                log.info("[NEXUS-LIKE] Redis connection established. Atomic Lua like counter active.");
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("[NEXUS-LIKE] Redis ping failed \u2014 falling back to direct MySQL writes. Cause: {}", e.getMessage());
            }
        } else {
            log.info("[NEXUS-LIKE] Redis not configured. Using direct MySQL writes.");
        }
    }

    // =================================================
    //  Public API
    // =================================================

    /**
     * Toggle a like from {@code userId} on {@code postId}. If the user has
     * already liked the post the like is removed; otherwise it is added.
     *
     * @return the current total like count for the post
     */
    public long likePost(Long postId, Long userId) {
        if (redisAvailable) {
            return executeLikeToggle(postId, userId);
        }
        return likeViaMysql(postId, userId);
    }

    /**
     * Remove a like from {@code userId} on {@code postId}.  Idempotent \u2014
     * if the user has not previously liked the post the count stays unchanged.
     *
     * @return the current total like count for the post
     */
    public long unlikePost(Long postId, Long userId) {
        if (!redisAvailable) {
            return unlikeViaMysql(postId, userId);
        }
        return executeLikeToggle(postId, userId);
    }

    /**
     * Check whether {@code userId} has already liked {@code postId}.
     */
    public boolean isLiked(Long postId, Long userId) {
        if (!redisAvailable) {
            return vibePostMapper.countPostLike(postId, userId) > 0;
        }
        try {
            String key = LIKE_SET_PREFIX + postId;
            Boolean member = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
            return Boolean.TRUE.equals(member);
        } catch (Exception e) {
            log.warn("[NEXUS-LIKE] isLiked check failed for post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /**
     * Return the current like count for a post, reading from Redis when available
     * or from MySQL otherwise.
     */
    public long getLikeCount(Long postId) {
        if (redisAvailable) {
            try {
                String key = LIKE_SET_PREFIX + postId;
                Long count = stringRedisTemplate.opsForSet().size(key);
                return count != null ? count : 0;
            } catch (Exception e) {
                log.warn("[NEXUS-LIKE] getLikeCount from Redis failed for post {}: {}", postId, e.getMessage());
            }
        }
        VibePost post = vibePostMapper.selectById(postId);
        return post != null ? post.getLikeCount() : 0;
    }

    // =================================================
    //  Internal: Lua atomic path
    // =================================================

    private long executeLikeToggle(Long postId, Long userId) {
        if (stringRedisTemplate == null || likeToggleScript == null) {
            return likeViaMysql(postId, userId);
        }
        try {
            List<String> keys = Arrays.asList(
                LIKE_SET_PREFIX + postId,
                LIKE_DELTA_PREFIX + postId,
                DIRTY_SET_KEY,
                RANKING_KEY
            );
            Long count = stringRedisTemplate.execute(
                likeToggleScript,
                keys,
                userId.toString(),
                postId.toString(),
                DEFAULT_WEIGHT
            );
            return count != null ? count : 0;
        } catch (DataAccessException e) {
            log.warn("[NEXUS-LIKE] Lua script execution failed for post {}: {}", postId, e.getMessage());
            // Degrade gracefully
            return likeViaMysql(postId, userId);
        }
    }

    // =================================================
    //  Fallback: direct MySQL
    // =================================================

    private long likeViaMysql(Long postId, Long userId) {
        int inserted = 0;
        try {
            inserted = vibePostMapper.insertPostLike(postId, userId);
        } catch (DataAccessException e) {
            log.debug("[NEXUS-LIKE] User {} already liked post {}, keeping count stable.", userId, postId);
        }
        if (inserted > 0) {
            vibePostMapper.updateLikeCountDelta(postId, 1);
        }
        VibePost post = vibePostMapper.selectById(postId);
        long count = (post != null) ? post.getLikeCount() : 0;
        log.debug("[NEXUS-LIKE] Direct MySQL like on post {} (count={})", postId, count);
        return count;
    }

    private long unlikeViaMysql(Long postId, Long userId) {
        int deleted = vibePostMapper.deletePostLike(postId, userId);
        if (deleted > 0) {
            vibePostMapper.updateLikeCountDelta(postId, -1);
        }
        VibePost post = vibePostMapper.selectById(postId);
        long count = (post != null) ? Math.max(post.getLikeCount(), 0) : 0;
        log.debug("[NEXUS-LIKE] Direct MySQL unlike on post {} (count={})", postId, count);
        return count;
    }
}
