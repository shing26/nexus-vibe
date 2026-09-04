package com.nexus.campus.task;

import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drift reconciliation between the Redis like sets and MySQL like_count.
 *
 * <p>Normal operation intentionally leaves Redis ahead of MySQL between
 * flushes (write-behind), so small drifts are expected. This task samples
 * posts with a rotating id cursor and only acts when the drift looks like
 * Redis DATA LOSS — a lost set reads 0 (or far below the DB value), which a
 * future toggle would then overwrite INTO MySQL, zeroing the count
 * (see docs/research/async-pool-loadtest.md notes).</p>
 *
 * <p>Repair: replay the durable truth — re-add every user from
 * {@code vibe_post_like} into the Redis set, overwrite MySQL with SCARD,
 * and trigger the hot-ranking ZSET rebuild (a Redis loss leaves the ZSET
 * empty, which otherwise makes the hot page return empty until the next
 * hourly recalculation).</p>
 */
@Component
public class DriftReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(DriftReconcileTask.class);

    private static final String LIKE_SET_PREFIX = "post:like:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private com.nexus.campus.service.PostRankingService postRankingService;

    @Value("${campus.like.drift-enabled:true}")
    private boolean driftEnabled;

    /** Redis set smaller than ratio * DB count counts as data loss. */
    @Value("${campus.like.drift-ratio:0.5}")
    private double driftRatio;

    /** Absolute gap (DB - Redis) above which repair triggers. */
    @Value("${campus.like.drift-abs:100}")
    private long driftAbs;

    /** Posts sampled per cycle (rotating cursor). */
    @Value("${campus.like.sample-size:200}")
    private int sampleSize;

    private volatile long cursor;

    /**
     * Hourly sweep: sample a window of posts, repair Redis-loss-shaped drift.
     */
    @Scheduled(cron = "0 40 * * * ?")
    public void reconcileDrift() {
        if (!driftEnabled || redisTemplate == null) {
            return;
        }
        try {
            List<VibePost> window = vibePostMapper.selectPostWindow(cursor, sampleSize);
            if (window.isEmpty()) {
                cursor = 0; // rotate
                return;
            }
            cursor = window.get(window.size() - 1).getId();

            int repaired = 0;
            for (VibePost post : window) {
                if (repairIfLost(post)) {
                    repaired++;
                }
            }
            if (repaired > 0) {
                log.warn("[DRIFT] Rebuilt {} like sets from vibe_post_like; triggering hot-ranking rebuild", repaired);
                postRankingService.recalculateHotRanking();
            }
        } catch (Exception e) {
            log.warn("[DRIFT] Reconcile cycle failed: {}", e.getMessage());
        }
    }

    /**
     * Returns true when the post's Redis set was detected lost and rebuilt.
     */
    boolean repairIfLost(VibePost post) {
        try {
            String key = LIKE_SET_PREFIX + post.getId();
            // the count IS the set cardinality
            Long size = redisTemplate.opsForSet().size(key);
            long redisCount = size != null ? size : 0;

            long dbCount = post.getLikeCount() != null ? post.getLikeCount() : 0;
            if (!isDataLossShape(dbCount, redisCount)) {
                return false;
            }

            List<Long> userIds = vibePostMapper.selectUserIdsByPostId(post.getId());
            redisTemplate.delete(key);
            for (Long userId : userIds) {
                redisTemplate.opsForSet().add(key, userId.toString());
            }
            // durable truth: the like rows themselves
            vibePostMapper.updateLikeCount(post.getId(), userIds.size());
            log.warn("[DRIFT] Post {}: Redis {} vs DB {} — rebuilt set with {} users from vibe_post_like",
                     post.getId(), redisCount, dbCount, userIds.size());
            return true;
        } catch (Exception e) {
            log.warn("[DRIFT] Repair check failed for post {}: {}", post.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Data-loss shape: the DB has real likes but Redis holds far fewer —
     * normal write-behind drift goes the OTHER direction (Redis >= DB).
     */
    private boolean isDataLossShape(long dbCount, long redisCount) {
        if (dbCount <= 0 || redisCount >= dbCount) {
            return false; // Redis ahead of/level with DB: normal
        }
        if (dbCount < driftAbs) {
            return false; // small counts: below the repair threshold
        }
        return redisCount < dbCount * driftRatio;
    }
}
