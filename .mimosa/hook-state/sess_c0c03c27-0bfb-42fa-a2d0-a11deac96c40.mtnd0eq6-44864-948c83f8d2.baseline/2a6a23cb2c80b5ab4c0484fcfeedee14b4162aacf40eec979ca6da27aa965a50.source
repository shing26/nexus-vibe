package com.nexus.campus.task;

import com.nexus.campus.mapper.VibePostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Scheduled task that flushes dirty like counters from Redis to MySQL.
 *
 * <p>Every 5 minutes, reads every post ID from the dirty set ({@code post:like:dirty}),
 * queries Redis for the current {@code SCARD}, and batch-updates the MySQL column.
 * Cleaned keys are removed from the dirty set to avoid redundant work.</p>
 */
@Component
public class LikeSyncTask {

    private static final Logger log = LoggerFactory.getLogger(LikeSyncTask.class);

    private static final String LIKE_SET_PREFIX = "post:like:";
    private static final String DIRTY_SET_KEY   = "post:like:dirty";

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private VibePostMapper vibePostMapper;

    /**
     * Every 5 minutes, flush dirty like counters from Redis to MySQL.
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void syncLikes() {
        if (redisTemplate == null) {
            log.debug("[LIKE-SYNC] Redis unavailable, skipping sync.");
            return;
        }

        Set<Object> dirtyPosts = redisTemplate.opsForSet().members(DIRTY_SET_KEY);
        if (dirtyPosts == null || dirtyPosts.isEmpty()) {
            log.debug("[LIKE-SYNC] No dirty posts to sync.");
            return;
        }

        int synced = 0;
        int failed = 0;

        for (Object postIdObj : dirtyPosts) {
            String postIdStr = postIdObj.toString();
            try {
                Long postId = Long.parseLong(postIdStr);
                String key = LIKE_SET_PREFIX + postId;
                Long redisCount = redisTemplate.opsForSet().size(key);

                if (redisCount != null) {
                    vibePostMapper.updateLikeCount(postId, redisCount.intValue());
                }

                redisTemplate.opsForSet().remove(DIRTY_SET_KEY, postIdStr);
                synced++;

            } catch (Exception e) {
                failed++;
                log.error("[LIKE-SYNC] Failed to sync post {}: {}", postIdStr, e.getMessage());
            }
        }

        if (synced > 0 || failed > 0) {
            log.info("[LIKE-SYNC] Batch sync complete - {} synced, {} failed", synced, failed);
        }
    }
}
