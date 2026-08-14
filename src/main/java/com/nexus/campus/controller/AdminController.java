package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.AuditRequest;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.service.VibePostService;
import com.nexus.campus.service.PostSearchService;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    private VibePostService vibePostService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private VibeCommentMapper vibeCommentMapper;

    @Autowired
    private PostSearchService postSearchService;

    @GetMapping("/pending-posts")
    public ApiResponse<List<PostPageVo>> getPendingPosts(@RequestAttribute("currentRole") String role) {
        ApiResponse check = checkAdmin(role);
        if (check != null) return check;
        List<PostPageVo> posts = vibePostService.getPendingAuditPosts();
        if (posts == null) {
            posts = Collections.emptyList();
        }
        return ApiResponse.success(posts);
    }

    @PostMapping("/audit/{id}")
    public ApiResponse<Void> auditPost(
            @PathVariable Long id,
            @RequestBody AuditRequest request,
            @RequestAttribute("currentRole") String role) {
        ApiResponse check = checkAdmin(role);
        if (check != null) return check;
        if (request == null || request.getAction() == null) {
            return ApiResponse.error(400, "Missing 'action' field.");
        }
        switch (request.getAction().toUpperCase()) {
            case "APPROVED":
                vibePostService.approvePost(id);
                return ApiResponse.successMessage("Post approved and published.");
            case "REJECTED":
                vibePostService.rejectPost(id);
                return ApiResponse.successMessage("Post rejected.");
            default:
                return ApiResponse.error(400, "Invalid action: " + request.getAction());
        }
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(@RequestAttribute("currentRole") String role) {
        ApiResponse check = checkAdmin(role);
        if (check != null) return check;
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", sysUserMapper.selectCount(null));
        stats.put("totalPosts", vibePostMapper.selectCount(null));
        stats.put("totalComments", vibeCommentMapper.selectCount(null));
        stats.put("pendingAudit", vibePostMapper.selectPendingAuditPosts().size());
        return ApiResponse.success(stats);
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard(@RequestAttribute("currentRole") String role) {
        ApiResponse check = checkAdmin(role);
        if (check != null) return check;
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalPosts", vibePostMapper.selectCount(null));
        dashboard.put("pendingAudits", vibePostMapper.selectPendingAuditPosts().size());
        dashboard.put("todayPosts", vibePostMapper.countTodayPosts());
        return ApiResponse.success(dashboard);
    }

    @GetMapping("/audit/posts")
    public ApiResponse<List<PostPageVo>> getPendingAuditPosts(@RequestAttribute("currentRole") String role) {
        ApiResponse check = checkAdmin(role);
        if (check != null) return check;
        return ApiResponse.success(vibePostService.getPendingAuditPosts());
    }

    @PostMapping("/audit/posts/{id}/approve")
    public ApiResponse<Void> approvePost(@PathVariable Long id, @RequestAttribute("currentRole") String role) {
        ApiResponse check = checkAdmin(role);
        if (check != null) return check;
        vibePostService.approvePost(id);
        return ApiResponse.successMessage("Post approved and published.");
    }

    @PostMapping("/audit/posts/{id}/reject")
    public ApiResponse<Void> rejectPost(@PathVariable Long id, @RequestAttribute("currentRole") String role) {
        ApiResponse check = checkAdmin(role);
        if (check != null) return check;
        vibePostService.rejectPost(id);
       return ApiResponse.successMessage("Post rejected.");
   }

    @PostMapping("/search/reindex")
    public ApiResponse<Map<String, Object>> reindexSearch(@RequestAttribute("currentRole") String role) {
        ApiResponse check = checkAdmin(role);
        if (check != null) return check;
        List<VibePost> posts = vibePostMapper.selectActivePostsOrdered();
        Map<String, Object> data = new HashMap<>();
        data.put("reindexed", postSearchService.rebuildIndex(posts));
        data.put("esAvailable", postSearchService.isAvailable());
        return ApiResponse.success(data);
    }

    private ApiResponse checkAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            return ApiResponse.forbidden("Access denied. Admin privileges required.");
        }
        return null;
    }
}
