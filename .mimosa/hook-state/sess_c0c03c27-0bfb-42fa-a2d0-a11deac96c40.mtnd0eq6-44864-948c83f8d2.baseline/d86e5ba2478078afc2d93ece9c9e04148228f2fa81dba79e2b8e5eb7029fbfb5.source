package com.nexus.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.campus.dto.PageResult;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gravity-decay hot ranking backed by Redis ZSET with hourly recalculation.
 *
 * <p>Ranking key: {@code post:ranking:likes}  \u2014 member = postId, score = gravity-decay score.
 * Real-time like/unlike events incrementally adjust the score via the
 * {@code LikeCounterService} Lua script; a scheduled task recalculates the
 * full gravity-decay formula every hour for posts created within the last 7 days.</p>
 *
 * <p>Formula: (L * 10 + C * 20 + V * 1) / Math.pow(ageInHours + 2, 1.5)</p>
 *
 * <p>Gracefully degrades to MySQL ORDER BY when Redis is unavailable.</p>
 */
@Service
public class PostRankingService {

    private static final Logger log = LoggerFactory.getLogger(PostRankingService.class);

    /** Must match the constant used in {@link LikeCounterService#RANKING_KEY}. */
    static final String RANKING_KEY = "post:ranking:likes";

    private static final int RECALCULATION_DAYS = 7;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private VibePostMapper vibePostMapper;

    private boolean redisAvailable;

    @PostConstruct
    void init() {
        redisAvailable = (stringRedisTemplate != null);
        if (redisAvailable) {
            try {
                RedisConnection conn = stringRedisTemplate.getConnectionFactory().getConnection();
                conn.ping();
                conn.close();
                log.info("[NEXUS-RANKING] Redis connection established. Gravity-decay ranking active.");
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("[NEXUS-RANKING] Redis unavailable, ranking degraded to MySQL. Cause: {}", e.getMessage());
            }
        } else {
            log.info("[NEXUS-RANKING] Redis not configured, ranking degraded to MySQL.");
        }
    }

    // =================================================
    //  Public API
    // =================================================

    /**
     * Get a page of hot posts ordered by gravity-decay score descending.
     *
     * @param page 1-based page number
     * @param size page size
     * @return paginated hot posts
     */
    /**
     * Get the top N hot posts (backward-compatible convenience wrapper).
     *
     * @param limit number of posts to return
     * @return list of PostPageVo sorted by gravity-decay score descending
     */
    public List<PostPageVo> getHotPosts(int limit) {
        PageResult<PostPageVo> pageResult = getHotRankPage(1, limit);
        return pageResult.getList();
    }

    public PageResult<PostPageVo> getHotRankPage(int page, int size) {
        if (redisAvailable) {
            try {
                long start = (long) (page - 1) * size;
                long end = start + size - 1;

                Set<ZSetOperations.TypedTuple<String>> top =
                        stringRedisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, start, end);

                if (top == null || top.isEmpty()) {
                    return PageResult.of(page, size, 0, List.of());
                }

                List<Long> postIds = top.stream()
                        .map(t -> Long.parseLong(t.getValue()))
                        .collect(Collectors.toList());

                Long total = stringRedisTemplate.opsForZSet().zCard(RANKING_KEY);
                long totalVal = total != null ? total : 0;

                List<PostPageVo> vos = fetchAndOrderByIds(postIds);
                return PageResult.of(page, size, totalVal, vos);
            } catch (Exception e) {
                log.warn("[NEXUS-RANKING] Redis read failed, falling back to MySQL: {}", e.getMessage());
            }
        }
        return getFallbackHotRankPage(page, size);
    }

    // =================================================
    //  Scheduled recalculation (hourly)
    // =================================================

    /**
     * Recalculate all hot-post scores using the gravity-decay formula every hour.
     * Posts older than 7 days are removed from the ranking.
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void recalculateHotRanking() {
        if (!redisAvailable) return;

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minusDays(RECALCULATION_DAYS);

            List<VibePost> posts = vibePostMapper.selectList(
                    new LambdaQueryWrapper<VibePost>()
                            .eq(VibePost::getStatus, 1)
                            .ge(VibePost::getCreateTime, cutoff)
                            .orderByDesc(VibePost::getCreateTime)
            );

            if (posts == null || posts.isEmpty()) {
                log.debug("[NEXUS-RANKING] No recent posts to recalculate.");
                return;
            }

            // Delete old ranking and rebuild
            stringRedisTemplate.delete(RANKING_KEY);
            ZSetOperations<String, String> zset = stringRedisTemplate.opsForZSet();

            int count = 0;
            for (VibePost post : posts) {
                double score = computeGravityScore(post, now);
                if (score > 0) {
                    zset.add(RANKING_KEY, post.getId().toString(), score);
                    count++;
                }
            }

            log.info("[NEXUS-RANKING] Ranking recalculated \u2014 {} posts scored (past {} days).", count, RECALCULATION_DAYS);
        } catch (Exception e) {
            log.warn("[NEXUS-RANKING] Recalculation failed: {}", e.getMessage());
        }
    }

    // =================================================
    //  Gravity-decay formula
    // =================================================

    static double computeGravityScore(VibePost post, LocalDateTime now) {
        long likeCount    = post.getLikeCount() != null ? post.getLikeCount() : 0;
        long commentCount = post.getCommentCount() != null ? post.getCommentCount() : 0;
        long viewCount    = post.getViewCount() != null ? post.getViewCount() : 0;

        double ageInHours = ChronoUnit.HOURS.between(post.getCreateTime(), now);
        if (ageInHours < 0) ageInHours = 0; // safeguard for future-dated posts

        return (likeCount * 10 + commentCount * 20 + viewCount * 1)
                / Math.pow(ageInHours + 2, 1.5);
    }

    // =================================================
    //  Internal helpers
    // =================================================

    private List<PostPageVo> fetchAndOrderByIds(List<Long> postIds) {
        if (postIds.isEmpty()) return List.of();

        List<VibePost> posts = vibePostMapper.selectByIdsOrdered(postIds);
        if (posts == null || posts.isEmpty()) return List.of();

        // Preserve order from postIds (which is the Redis rank order)
        java.util.Map<Long, VibePost> map = new java.util.HashMap<>();
        for (VibePost p : posts) {
            map.put(p.getId(), p);
        }
        List<VibePost> ordered = new java.util.ArrayList<>();
        for (Long id : postIds) {
            VibePost p = map.get(id);
            if (p != null) {
                ordered.add(p);
            }
        }
        return ordered.stream().map(this::convertToPageVo).collect(java.util.stream.Collectors.toList());
    }

    private PageResult<PostPageVo> getFallbackHotRankPage(int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(RECALCULATION_DAYS);

        List<VibePost> allPosts = vibePostMapper.selectList(
                new LambdaQueryWrapper<VibePost>()
                        .eq(VibePost::getStatus, 1)
                        .ge(VibePost::getCreateTime, cutoff)
        );

        if (allPosts == null || allPosts.isEmpty()) {
            return PageResult.of(page, size, 0, List.of());
        }

        // Compute gravity-decay scores and sort
        List<ScoredPost> scored = new ArrayList<>();
        for (VibePost p : allPosts) {
            double score = computeGravityScore(p, now);
            scored.add(new ScoredPost(p, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredPost::score).reversed());

        // Paginate
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, scored.size());

        if (fromIndex >= scored.size()) {
            return PageResult.of(page, size, scored.size(), List.of());
        }

        List<PostPageVo> vos = scored.subList(fromIndex, toIndex).stream()
                .map(sp -> convertToPageVo(sp.post()))
                .collect(Collectors.toList());

        return PageResult.of(page, size, scored.size(), vos);
    }

    private PostPageVo convertToPageVo(VibePost post) {
        PostPageVo vo = new PostPageVo();
        BeanUtils.copyProperties(post, vo);
        return vo;
    }

    /**
     * Update a post's ZSET score when a like event occurs (backward-compatible).
     * Called by deprecated {@code VibePostServiceImpl.likePost}.
     */
    public void onLike(Long postId, long currentLikeCount) {
        if (!redisAvailable) return;
        try {
            stringRedisTemplate.opsForZSet().add(RANKING_KEY, postId.toString(), currentLikeCount);
        } catch (Exception e) {
            log.warn("[NEXUS-RANKING] Failed to update ranking for post {}: {}", postId, e.getMessage());
        }
    }

    private record ScoredPost(VibePost post, double score) {}
}
