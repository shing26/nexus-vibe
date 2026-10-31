package com.nexus.campus.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.AuditRequest;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.dto.ResetPasswordRequest;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.security.AdminGuard;
import com.nexus.campus.service.VibePostService;
import com.nexus.campus.service.PostSearchService;
import com.nexus.campus.service.SysUserService;
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

    @Autowired
    private SysUserService sysUserService;

    /**
     * Stopgap password recovery while the platform has no mail delivery:
     * generates a random temporary password and returns it to the admin, who
     * hands it to the user out-of-band. The user should change it after login.
     */
    @PostMapping("/users/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(
            @RequestBody ResetPasswordRequest request,
            @RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()) {
            throw BusinessException.badRequest("Missing 'username' field.");
        }
        // No catch: the service names its own status now, so a missing account is
        // 404 and a write that failed is 500. Echoing e.getMessage() here used to
        // report the second as the first.
        String tempPassword = sysUserService.resetPassword(request.getUsername().trim());
        Map<String, Object> data = new HashMap<>();
        data.put("username", request.getUsername().trim());
        data.put("tempPassword", tempPassword);
        return ApiResponse.success("Temporary password generated. Deliver it to the user out-of-band and ask them to change it after login.", data);
    }

    @GetMapping("/pending-posts")
    public ApiResponse<List<PostPageVo>> getPendingPosts(@RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
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
        AdminGuard.requireAdmin(role);
        if (request == null || request.getAction() == null) {
            throw BusinessException.badRequest("Missing 'action' field.");
        }
        switch (request.getAction().toUpperCase()) {
            case "APPROVED":
                vibePostService.approvePost(id);
                return ApiResponse.successMessage("Post approved and published.");
            case "REJECTED":
                vibePostService.rejectPost(id);
                return ApiResponse.successMessage("Post rejected.");
            default:
                throw BusinessException.badRequest("Invalid action: " + request.getAction());
        }
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(@RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", sysUserMapper.selectCount(null));
        stats.put("totalPosts", vibePostMapper.selectCount(null));
        stats.put("totalComments", vibeCommentMapper.selectCount(null));
        stats.put("pendingAudit", vibePostMapper.selectPendingAuditPosts().size());
        return ApiResponse.success(stats);
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard(@RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalPosts", vibePostMapper.selectCount(null));
        dashboard.put("pendingAudits", vibePostMapper.selectPendingAuditPosts().size());
        dashboard.put("todayPosts", vibePostMapper.countTodayPosts());
        return ApiResponse.success(dashboard);
    }

    @GetMapping("/audit/posts")
    public ApiResponse<List<PostPageVo>> getPendingAuditPosts(@RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        return ApiResponse.success(vibePostService.getPendingAuditPosts());
    }

    @PostMapping("/audit/posts/{id}/approve")
    public ApiResponse<Void> approvePost(@PathVariable Long id, @RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        vibePostService.approvePost(id);
        return ApiResponse.successMessage("Post approved and published.");
    }

    @PostMapping("/audit/posts/{id}/reject")
    public ApiResponse<Void> rejectPost(@PathVariable Long id, @RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        vibePostService.rejectPost(id);
        return ApiResponse.successMessage("Post rejected.");
    }

    /**
     * The comment audit queue holds both sources that write status 2: DFA
     * critical hits and the comment-safety path's suspected prompt injection
     * (ADR-0017). Approve returns the comment to its thread; reject hides it.
     */
    @GetMapping("/audit/comments")
    public ApiResponse<List<VibeComment>> getPendingAuditComments(@RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        return ApiResponse.success(vibeCommentMapper.selectCommentsInAuditQueue());
    }

    @PostMapping("/audit/comments/{id}/approve")
    public ApiResponse<Void> approveComment(@PathVariable Long id, @RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        VibeComment approved = auditComment(id, 1);
        if (approved != null) {
            vibePostMapper.recalculateCommentCount(approved.getPostId());
        }
        return ApiResponse.successMessage("Comment approved and published.");
    }

    @PostMapping("/audit/comments/{id}/reject")
    public ApiResponse<Void> rejectComment(@PathVariable Long id, @RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        VibeComment rejected = auditComment(id, 0);
        if (rejected != null) {
            vibePostMapper.recalculateCommentCount(rejected.getPostId());
        }
        return ApiResponse.successMessage("Comment rejected.");
    }

    /**
     * Conditional transition out of the audit queue: an already-audited or
     * deleted comment returns null rather than being moved twice.
     */
    private VibeComment auditComment(Long id, int targetStatus) {
        VibeComment comment = vibeCommentMapper.selectById(id);
        if (comment == null) {
            throw BusinessException.notFound("Comment not found.");
        }
        int updated = vibeCommentMapper.update(null,
                new LambdaUpdateWrapper<VibeComment>()
                        .eq(VibeComment::getId, id)
                        .eq(VibeComment::getStatus, 2)
                        .set(VibeComment::getStatus, targetStatus));
        return updated > 0 ? comment : null;
    }

    @PostMapping("/search/reindex")
    public ApiResponse<Map<String, Object>> reindexSearch(@RequestAttribute("currentRole") String role) {
        AdminGuard.requireAdmin(role);
        List<VibePost> posts = vibePostMapper.selectActivePostsOrdered();
        PostSearchService.BulkResult result = postSearchService.rebuildIndex(posts);
        Map<String, Object> data = new HashMap<>();
        // reindexed is what Elasticsearch confirmed, not how many rows were read out of MySQL.
        data.put("reindexed", result.indexed());
        data.put("requested", result.submitted());
        data.put("failed", result.failed());
        data.put("complete", result.complete());
        data.put("esAvailable", postSearchService.isAvailable());
        return ApiResponse.success(data);
    }
}
