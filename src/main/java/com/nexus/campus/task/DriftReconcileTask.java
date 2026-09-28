package com.nexus.campus.task;

import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.util.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drift reconciliation between the Redis like sets and the durable membership
 * table, {@code vibe_post_like}.
 *
 * <p>The normal toggle path mirrors membership into `vibe_post_like`.
 * Reconciliation takes the union of the Redis set and the DB rows instead of
 * choosing one side: an empty table can be bootstrapped from an existing Redis
 * set, while a lost Redis set can be rebuilt from the table. Repair never
 * removes a member; a stale member is less harmful than erasing a real like.
 * The <em>count</em> is a different matter: it follows the union, so a post that
 * both sources agree is unliked has its {@code like_count} reset to zero rather
 * than kept for manual recovery — see {@code docs/tickets/like-count-convergence.md}.</p>
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

    /** Posts sampled per cycle (rotating cursor). */
    @Value("${campus.like.sample-size:200}")
    private int sampleSize;

    private volatile long cursor;

    /**
     * Run once shortly after startup, then hourly. The early pass exists so a
     * deployment with a pre-existing Redis-only set is backfilled without
     * waiting for the first cron boundary.
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void reconcileDrift() {
        TraceIds.runAsJob("like-drift-reconcile", this::reconcileDriftOnce);
    }

    private void reconcileDriftOnce() {
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
                if (reconcilePost(post)) {
                    repaired++;
                }
            }
            if (repaired > 0) {
                log.warn("[DRIFT] Reconciled {} like memberships; triggering hot-ranking rebuild", repaired);
                postRankingService.recalculateHotRanking();
            }
        } catch (Exception e) {
            log.warn("[DRIFT] Reconcile cycle failed: {}", e.getMessage());
        }
    }

    /**
     * Reconciles one post's membership and count. Returns true when anything changed.
     */
    boolean reconcilePost(VibePost post) {
        try {
            String key = LIKE_SET_PREFIX + post.getId();
            // the count IS the set cardinality
            Long size = redisTemplate.opsForSet().size(key);
            long redisCount = size != null ? size : 0;

            Set<String> redisUserIds = redisTemplate.opsForSet().members(key);
            if (redisUserIds == null) {
                redisUserIds = Set.of();
            }
            List<Long> dbUserIds = vibePostMapper.selectUserIdsByPostId(post.getId());
            if (dbUserIds == null) {
                dbUserIds = List.of();
            }

            Set<Long> redisIds = new HashSet<>();
            for (String redisUserId : redisUserIds) {
                try {
                    redisIds.add(Long.parseLong(redisUserId));
                } catch (NumberFormatException e) {
                    log.warn("[DRIFT] Ignoring malformed Redis like member '{}' for post {}",
                            redisUserId, post.getId());
                }
            }
            Set<Long> durableIds = new HashSet<>(dbUserIds);
            Set<Long> union = new HashSet<>(durableIds);
            union.addAll(redisIds);

            if (redisIds.equals(durableIds)) {
                long storedCount = post.getLikeCount() != null ? post.getLikeCount() : 0;
                if (redisIds.isEmpty() && storedCount > 0) {
                    // Both authoritative sources say "nobody likes this", which is
                    // exactly what a full unlike looks like once the set and the
                    // table are both empty. The table is written on every toggle,
                    // so an empty table is a statement rather than a gap: keeping
                    // the old count made every unlike-to-zero a permanent wrong
                    // value in the column the hot ranking sorts on.
                    vibePostMapper.updateLikeCount(post.getId(), 0);
                    log.warn("[DRIFT] Post {} has no members in Redis or the membership table; like_count {} reset to 0",
                            post.getId(), storedCount);
                    return true;
                }
                if (!redisIds.isEmpty() && storedCount != redisIds.size()) {
                    vibePostMapper.updateLikeCount(post.getId(), redisIds.size());
                    log.warn("[DRIFT] Post {} membership agrees at {} but like_count was {}; repairing the denormalised count",
                            post.getId(), redisIds.size(), storedCount);
                    return true;
                }
                return false;
            }

            for (Long userId : redisIds) {
                if (!durableIds.contains(userId)) {
                    vibePostMapper.insertPostLike(post.getId(), userId);
                }
            }
            for (Long userId : durableIds) {
                if (!redisIds.contains(userId)) {
                    redisTemplate.opsForSet().add(key, userId.toString());
                }
            }

            // The union is the most conservative recoverable membership set.
            // Update the denormalised count only after both durable directions
            // have been replayed.
            vibePostMapper.updateLikeCount(post.getId(), union.size());
            log.warn("[DRIFT] Post {} reconciled membership: Redis={}, DB={}, union={}",
                    post.getId(), redisCount, durableIds.size(), union.size());
            return true;
        } catch (Exception e) {
            log.warn("[DRIFT] Repair check failed for post {}: {}", post.getId(), e.getMessage());
            return false;
        }
    }

}
