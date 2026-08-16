package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.event.MessageEvent;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.*;
import com.nexus.campus.task.LikeSyncTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Slf4j
@Profile("!prod")
public class DemoShowcaseController {

    private final VibePostMapper vibePostMapper;
    private final PostRankingService postRankingService;
    private final LikeCounterService likeCounterService;
    private final LikeSyncTask likeSyncTask;
    private final MessageNotificationService messageNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final SensitiveWordService sensitiveWordService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/seed-hot-posts")
    public ApiResponse<String> seedHotPosts() {
        List<VibePost> mockPosts = Arrays.asList(
            createMockPost("Old post from 7 days ago: Classic Java full-stack tutorial", 500, 100, 2000, LocalDateTime.now().minusDays(7)),
            createMockPost("New post from 2 hours ago: Fall recruitment project Nexus refactor", 120, 30, 500, LocalDateTime.now().minusHours(2)),
            createMockPost("Post from 1 day ago: How is the cafeteria food today?", 10, 2, 50, LocalDateTime.now().minusDays(1))
        );
        for (VibePost post : mockPosts) {
            vibePostMapper.insert(post);
        }

        postRankingService.recalculateHotRanking();
        return ApiResponse.success("Test posts generated, Gravity Decay hot ranking triggered");
    }

    @PostMapping("/burst-like")
    public ApiResponse<Map<String, Object>> burstLike(@RequestParam Long postId, @RequestParam Long userId) {
        long realtimeCount = likeCounterService.likePost(postId, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("likeStatus", likeCounterService.isLiked(postId, userId) ? "liked" : "unliked");
        result.put("redisRealTimeCount", realtimeCount);

        if (stringRedisTemplate != null) {
            Object delta = stringRedisTemplate.opsForHash().get("post:like:delta:" + postId, "delta");
            result.put("redisDelta", (delta != null ? delta : "0") + " (async pending flush)");
        } else {
            result.put("redisDelta", "Redis not enabled, using direct MySQL mode");
        }

        VibePost dbPost = vibePostMapper.selectById(postId);
        result.put("mysqlLikeCount", (dbPost != null ? dbPost.getLikeCount() : 0) + " (stale until sync)");
        return ApiResponse.success(result);
    }

    @PostMapping("/trigger-sync")
    public ApiResponse<String> triggerSync() {
        likeSyncTask.syncLikes();
        return ApiResponse.success("LikeSyncTask executed, Redis deltas flushed to MySQL");
    }

    @PostMapping("/add-sensitive-word")
    public ApiResponse<String> addSensitiveWord(@RequestParam String word) {
        if (stringRedisTemplate == null) {
            return ApiResponse.error(503, "Redis not available. Demo requires Redis for hot-reload.");
        }

        stringRedisTemplate.opsForSet().add("sys:sensitive:words", word);

        Set<String> existing = stringRedisTemplate.opsForSet().members("sys:sensitive:words");
        if (existing == null) existing = new HashSet<>();
        existing.add(word);
        try {
            String json = new ObjectMapper().writeValueAsString(new ArrayList<>(existing));
            stringRedisTemplate.convertAndSend("channel:sensitive:words:update", json);
        } catch (Exception e) {
            log.warn("Failed to serialize word list", e);
        }

        return ApiResponse.success("Sensitive word [" + word + "] hot-loaded! Total: " + existing.size() + " words.");
    }

    @PostMapping("/check-text")
    public ApiResponse<PostAuditResult> checkText(@RequestParam String text) {
        PostAuditResult result = sensitiveWordService.checkText(text);
        return ApiResponse.success("Text audit complete", result);
    }

    @PostMapping("/trigger-message-event")
    public ApiResponse<Map<String, Object>> triggerMessageEvent(@RequestParam Long targetUserId) {
        eventPublisher.publishEvent(new MessageEvent(this, 1L, targetUserId, "COMMENT", "Someone replied to your post!", 101L));

        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        long unreadCount = messageNotificationService.getUnreadCount(targetUserId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetUserId", targetUserId);
        result.put("unreadCount", unreadCount);
        return ApiResponse.success(result);
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", "Nexus-Vibe Demo Showcase");
        info.put("features", Arrays.asList(
            "Gravity Decay Hot Ranking",
            "Lua Atomic Like + Write-Behind Sync",
            "Redis Sliding Window Rate Limit",
            "DFA Sensitive Word Hot Reload",
            "Async Message Decoupling + Unread Badge"
        ));
        return ApiResponse.success(info);
    }

    private VibePost createMockPost(String title, int likes, int comments, int views, LocalDateTime createTime) {
        VibePost post = new VibePost();
        post.setTitle(title);
        post.setContent("Test content: " + title);
        post.setUserId(1L);
        post.setCategoryId(1);
        post.setLikeCount(likes);
        post.setCommentCount(comments);
        post.setViewCount(views);
        post.setStatus(1);
        post.setIsPinned(0);
        post.setCreateTime(createTime);
        return post;
    }
}
