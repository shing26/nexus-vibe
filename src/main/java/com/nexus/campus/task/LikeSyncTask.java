package com.nexus.campus.task;

import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.util.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Scheduled task that flushes dirty like counters from Redis to MySQL.
 *
 * <p>Every 5 minutes, reads every post ID from the dirty set ({@code post:like:dirty}),
 * queries Redis for the current {@code SCARD}, and batch-updates the MySQL column.
 * Cleaned keys are removed from the dirty set to avoid redundant work.</p>
 *
 * <p>Only a toggle puts a post into the dirty set, so an empty Redis set seen
 * here means the last toggle was an unlike — not that the set was lost. The
 * durable membership table is the authority for who liked what, so a zero
 * {@code SCARD} is reconciled against it: rows still present are replayed back
 * into Redis (a lagging delete, or a set lost between the toggle and this
 * flush), and an empty table writes zero. The previous "refuse to write zero
 * when anything still says otherwise" guard made every legitimate unlike-to-zero
 * permanent — see {@code docs/tickets/like-count-convergence.md}.</p>
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

                if (redisCount == null) {
                    requeue(DIRTY_SET_KEY, postIdStr);
                    log.warn("[LIKE-SYNC] Redis set missing for post {}; left dirty for reconciliation", postId);
                    continue;
                }

                if (redisCount == 0) {
                    long durableCount = vibePostMapper.countPostLikes(postId);
                    if (durableCount > 0) {
                        // Redis lost the set (or a delete lagged): the table still
                        // holds the members, so replay them instead of dropping them.
                        rebuildRedisSetFromTable(postId, key);
                        log.warn("[LIKE-SYNC] Post {} had an empty Redis set but {} durable membership rows; rebuilt the set from the table",
                                postId, durableCount);
                        vibePostMapper.updateLikeCount(postId, (int) durableCount);
                    } else {
                        // Both sources agree the post has no likes: this is a real
                        // unlike-to-zero, and the denormalised count must follow.
                        vibePostMapper.updateLikeCount(postId, 0);
                        log.info("[LIKE-SYNC] Post {} has no likes in Redis or the membership table; like_count set to 0",
                                postId);
                    }
                    synced++;
                    continue;
                }

                vibePostMapper.updateLikeCount(postId, redisCount.intValue());
                synced++;

            } catch (Exception e) {
                failed++;
                requeue(DIRTY_SET_KEY, postIdStr);
                log.error("[LIKE-SYNC] Failed to sync post {}: {}", postIdStr, e.getMessage());
            }
        }

        if (synced > 0 || failed > 0) {
            log.info("[LIKE-SYNC] Batch sync complete - {} synced, {} failed, {} left in queue",
                     synced, failed, batch.size() - synced);
        }
    }

    private void requeue(String dirtyKey, String postId) {
        redisTemplate.opsForSet().add(dirtyKey, postId);
    }

    /**
     * Replays the durable membership rows back into the Redis set. Best-effort:
     * a failure here costs a cache miss, not the count, because the caller has
     * already written the durable count to MySQL.
     */
    private void rebuildRedisSetFromTable(Long postId, String key) {
        try {
            List<Long> members = vibePostMapper.selectUserIdsByPostId(postId);
            if (members == null) {
                return;
            }
            for (Long member : members) {
                redisTemplate.opsForSet().add(key, member.toString());
            }
        } catch (Exception e) {
            log.warn("[LIKE-SYNC] Failed to rebuild the Redis set for post {}: {}", postId, e.getMessage());
        }
    }
}
