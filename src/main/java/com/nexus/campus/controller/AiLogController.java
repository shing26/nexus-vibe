package com.nexus.campus.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.dto.AiReviewDetail;
import com.nexus.campus.dto.AiLogVo;
import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.PageResult;
import com.nexus.campus.dto.UserReviewEntry;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.security.AdminGuard;
import com.nexus.campus.service.AiReviewDetailService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/agent-logs")
public class AiLogController {

    private static final String SEVERITY_CRITICAL = "critical";
    private static final String SEVERITY_HIGH = "high";
    private static final String SEVERITY_MEDIUM = "medium";
    private static final String SEVERITY_LOW = "low";
    private static final String SEVERITY_UNKNOWN = "unknown";

    /** The evidence index is a curve, not a list page: truncate rather than paginate. */
    private static final int USER_REVIEW_LIMIT = 50;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private AiReviewDetailService aiReviewDetailService;

    @GetMapping
    public ApiResponse<PageResult<AiLogVo>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String reviewer,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long postId,
            @RequestAttribute(value = "currentRole", required = false) String role) {
        AdminGuard.requireAdmin(role);
        LambdaQueryWrapper<AiReviewLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(reviewer)) {
            wrapper.eq(AiReviewLog::getReviewer, reviewer);
        }
        if (StringUtils.hasText(severity)) {
            wrapper.eq(AiReviewLog::getSeverity, severity);
        }
        if (postId != null) {
            wrapper.eq(AiReviewLog::getPostId, postId);
        }
        wrapper.orderByDesc(AiReviewLog::getCreatedAt);

        Page<AiReviewLog> result = aiReviewLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<AiLogVo> list = toVos(result.getRecords());
        return ApiResponse.success("OK", PageResult.of(page, size, result.getTotal(), list));
    }

    @GetMapping("/ticker")
    public ApiResponse<List<AiLogVo>> getTicker() {
        Page<AiReviewLog> result = aiReviewLogMapper.selectPage(
                new Page<>(1, 6),
                new LambdaQueryWrapper<AiReviewLog>().orderByDesc(AiReviewLog::getCreatedAt));
        return ApiResponse.success(toVos(result.getRecords()));
    }

    @GetMapping("/post/{postId}/latest")
    public ApiResponse<AiReviewDetail> getLatestCodeReview(@PathVariable Long postId) {
        AiReviewLog log = aiReviewLogMapper.selectLatestCodeReviewByPostId(postId);
        return ApiResponse.success(aiReviewDetailService.toDetail(log));
    }

    /**
     * Public review history for a post's detail page: completed code reviews,
     * newest first, so earlier scores stay visible after a re-review lands.
     */
    @GetMapping("/post/{postId}")
    public ApiResponse<List<AiReviewDetail>> getPostReviewHistory(@PathVariable Long postId) {
        List<AiReviewLog> logs = aiReviewLogMapper.selectList(new LambdaQueryWrapper<AiReviewLog>()
                .eq(AiReviewLog::getPostId, postId)
                .eq(AiReviewLog::getReviewer, "code-review-agent")
                .isNotNull(AiReviewLog::getResultJson)
                .orderByDesc(AiReviewLog::getId)
                .last("LIMIT 20"));
        List<AiReviewDetail> details = new ArrayList<>(logs.size());
        for (AiReviewLog log : logs) {
            details.add(aiReviewDetailService.toDetail(log));
        }
        return ApiResponse.success(details);
    }

    /**
     * Public evidence index for a profile: the latest completed code review of
     * every active post the user wrote, newest first, capped at 50. Uses the
     * same "latest row per post" population as
     * {@code UserProfileSummaryService.calculateAverageAiScore}, so the
     * profile's reviewed-post count and average score can be recomputed from
     * these entries. An unknown user is an empty index, not a 404.
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<UserReviewEntry>> getUserReviewIndex(@PathVariable Long userId) {
        List<AiReviewLog> logs = aiReviewLogMapper.selectLatestCodeReviewLogsByUserId(userId);

        // The mapper returns newest first. Keep the first row per post even when
        // it has no payload: falling back to an older row would publish a score
        // the profile average does not use.
        List<AiReviewLog> latestPerPost = new ArrayList<>();
        Set<Long> seenPostIds = new HashSet<>();
        for (AiReviewLog log : logs) {
            if (log.getPostId() == null || !seenPostIds.add(log.getPostId())) {
                continue;
            }
            if (StringUtils.hasText(log.getResultJson())) {
                latestPerPost.add(log);
            }
        }
        if (latestPerPost.isEmpty()) {
            return ApiResponse.success(new ArrayList<>());
        }

        List<Long> postIds = new ArrayList<>(latestPerPost.size());
        for (AiReviewLog log : latestPerPost) {
            postIds.add(log.getPostId());
        }
        Map<Long, VibePost> visiblePosts = new HashMap<>();
        for (VibePost post : vibePostMapper.selectByIdsOrdered(postIds)) {
            if (post.getStatus() != null && post.getStatus() == 1) {
                visiblePosts.put(post.getId(), post);
            }
        }

        List<UserReviewEntry> entries = new ArrayList<>(Math.min(latestPerPost.size(), USER_REVIEW_LIMIT));
        for (AiReviewLog log : latestPerPost) {
            if (entries.size() >= USER_REVIEW_LIMIT) {
                break;
            }
            VibePost post = visiblePosts.get(log.getPostId());
            if (post == null) {
                // Deleted or no longer visible: no orphan entry with an empty title.
                continue;
            }
            entries.add(aiReviewDetailService.toUserReviewEntry(log, post.getTitle()));
        }
        return ApiResponse.success(entries);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(
            @RequestAttribute(value = "currentRole", required = false) String role) {
        AdminGuard.requireAdmin(role);
        List<AiReviewLog> logs = aiReviewLogMapper.selectList(null);
        long total = logs.size();
        long approved = logs.stream()
                .filter(log -> log.getIsApproved() != null && log.getIsApproved() == 1)
                .count();

        Map<String, Long> severityCounts = new HashMap<>();
        severityCounts.put(SEVERITY_CRITICAL, 0L);
        severityCounts.put(SEVERITY_HIGH, 0L);
        severityCounts.put(SEVERITY_MEDIUM, 0L);
        severityCounts.put(SEVERITY_LOW, 0L);
        severityCounts.put(SEVERITY_UNKNOWN, 0L);
        for (AiReviewLog log : logs) {
            severityCounts.merge(normalizeSeverity(log.getSeverity()), 1L, Long::sum);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalReviews", total);
        data.put("approved", approved);
        data.put("flagged", total - approved);
        data.put(SEVERITY_CRITICAL, severityCounts.get(SEVERITY_CRITICAL));
        data.put(SEVERITY_HIGH, severityCounts.get(SEVERITY_HIGH));
        data.put(SEVERITY_MEDIUM, severityCounts.get(SEVERITY_MEDIUM));
        data.put(SEVERITY_LOW, severityCounts.get(SEVERITY_LOW));
        data.put(SEVERITY_UNKNOWN, severityCounts.get(SEVERITY_UNKNOWN));
        data.put("avgResponseMs", null);
        return ApiResponse.success("OK", data);
    }

    private List<AiLogVo> toVos(List<AiReviewLog> logs) {
        List<AiLogVo> list = new ArrayList<>(logs.size());
        for (AiReviewLog log : logs) {
            AiLogVo vo = new AiLogVo();
            BeanUtils.copyProperties(log, vo);
            vo.setStatus(resolveReviewStatus(log.getResultJson()));
            vo.setPostTitle(resolvePostTitle(log.getPostId()));
            list.add(vo);
        }
        return list;
    }

    private String resolvePostTitle(Long postId) {
        if (postId == null) {
            return null;
        }
        VibePost post = vibePostMapper.selectById(postId);
        return post != null ? post.getTitle() : null;
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) {
            return SEVERITY_UNKNOWN;
        }
        String normalized = severity.trim().toLowerCase();
        if (SEVERITY_CRITICAL.equals(normalized)
                || SEVERITY_HIGH.equals(normalized)
                || SEVERITY_MEDIUM.equals(normalized)
                || SEVERITY_LOW.equals(normalized)
                || SEVERITY_UNKNOWN.equals(normalized)) {
            return normalized;
        }
        return SEVERITY_UNKNOWN;
    }

    private String resolveReviewStatus(String resultJson) {
        return StringUtils.hasText(resultJson) ? "completed" : "unavailable";
    }
}
