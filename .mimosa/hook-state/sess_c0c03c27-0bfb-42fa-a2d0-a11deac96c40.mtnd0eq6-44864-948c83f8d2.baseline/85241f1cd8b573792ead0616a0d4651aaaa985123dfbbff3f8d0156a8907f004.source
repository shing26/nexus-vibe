package com.nexus.campus.service;

import com.nexus.campus.dto.CommentCreateRequest;
import com.nexus.campus.entity.VibeComment;

import java.util.List;

public interface VibeCommentService {

    VibeComment createComment(CommentCreateRequest request, Long userId);

    List<VibeComment> getCommentsByPostId(Long postId);

    int countCommentsByPostId(Long postId);

    boolean deleteComment(Long commentId, Long userId, String role);
}
