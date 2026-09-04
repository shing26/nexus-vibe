package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.CommentCreateRequest;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.service.VibeCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    @Autowired
    private VibeCommentService vibeCommentService;

    @PostMapping
    public ApiResponse<VibeComment> createComment(
            @Valid @RequestBody CommentCreateRequest request,
            @RequestAttribute("currentUserId") Long userId) {
        VibeComment comment = vibeCommentService.createComment(request, userId);
        return ApiResponse.success("Comment transmitted.", comment);
    }

    @GetMapping("/post/{postId}")
    public ApiResponse<List<VibeComment>> getComments(@PathVariable Long postId) {
        return ApiResponse.success(vibeCommentService.getCommentsByPostId(postId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long id,
            @RequestAttribute("currentUserId") Long userId,
            @RequestAttribute("currentRole") String role) {
        boolean deleted = vibeCommentService.deleteComment(id, userId, role);
        if (!deleted) {
            return ApiResponse.notFound("Comment not found.");
        }
        return ApiResponse.successMessage("Comment deleted.");
    }
}
