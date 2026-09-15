package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.CommentCreateRequest;
import com.nexus.campus.dto.CommentVo;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.service.VibeCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    @Autowired
    private VibeCommentService vibeCommentService;

    @PostMapping
    public ApiResponse<CommentVo> createComment(
            @Valid @RequestBody CommentCreateRequest request,
            @RequestAttribute("currentUserId") Long userId) {
        VibeComment comment = vibeCommentService.createComment(request, userId);
        return ApiResponse.success("Comment transmitted.", CommentVo.from(comment));
    }

    @GetMapping("/post/{postId}")
    public ApiResponse<List<CommentVo>> getComments(@PathVariable Long postId) {
        List<CommentVo> vos = vibeCommentService.getCommentsByPostId(postId).stream()
                .map(CommentVo::from)
                .collect(Collectors.toList());
        return ApiResponse.success(vos);
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
