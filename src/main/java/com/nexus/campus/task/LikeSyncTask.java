package com.nexus.campus.task;

import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.util.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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

    /** SPOP batch size: atomic dequeue, bounded work per cycle. */
    static final int BATCH_SIZE = 100;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private VibePostMapper vibePostMapper;

    /**
     * Every 5 minutes, flush dirty like counters from Redis to MySQL.
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void syncLikes() {
        TraceIds.runAsJob("like-sync", this::syncLikesOnce);
    }

    private void syncLikesOnce() {
        if (redisTemplate == null) {
            log.debug("[LIKE-SYNC] Redis unavailable, skipping sync.");
            return;
        }

        // SPOP atomically dequeues the batch (no full-set SMEMBERS scan and no
        // double-processing across instances); failures are re-added below.
        List<Object> batch = redisTemplate.opsForSet().pop(DIRTY_SET_KEY, BATCH_SIZE);
        if (batch == null || batch.isEmpty()) {
            log.debug("[LIKE-SYNC] No dirty posts to sync.");
            return;
        }

        int synced = 0;
        int failed = 0;

        for (Object postIdObj : batch) {
            String postIdStr = postIdObj.toString();
            try {
                Long postId = Long.parseLong(postIdStr);
                String key = LIKE_SET_PREFIX + postId;
                Long redisCount = redisTemplate.opsForSet().size(key);

                if (redisCount != null) {
                    vibePostMapper.updateLikeCount(postId, redisCount.intValue());
                }

                synced++;

            } catch (Exception e) {
                failed++;
                // re-queue for the next cycle so the change is not lost
                redisTemplate.opsForSet().add(DIRTY_SET_KEY, postIdStr);
                log.error("[LIKE-SYNC] Failed to sync post {}: {}", postIdStr, e.getMessage());
            }
        }

        if (synced > 0 || failed > 0) {
            log.info("[LIKE-SYNC] Batch sync complete - {} synced, {} failed, {} left in queue",
                     synced, failed, batch.size() - synced);
        }
    }
}
