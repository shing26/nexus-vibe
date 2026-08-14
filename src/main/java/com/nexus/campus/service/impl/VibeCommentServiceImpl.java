package com.nexus.campus.service.impl;

import com.nexus.campus.dto.CommentCreateRequest;
import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.entity.*;
import com.nexus.campus.mapper.*;
import com.nexus.campus.service.SensitiveWordService;
import com.nexus.campus.service.VibeCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VibeCommentServiceImpl implements VibeCommentService {

    @Autowired
    private VibeCommentMapper vibeCommentMapper;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Override
    @Transactional
    public VibeComment createComment(CommentCreateRequest request, Long userId) {
        VibeComment comment = new VibeComment();
        comment.setPostId(request.getPostId());
        comment.setUserId(userId);
        comment.setParentId(request.getParentId() != null ? request.getParentId() : 0L);
        comment.setTargetId(request.getTargetId() != null ? request.getTargetId() : 0L);
        comment.setContent(request.getContent());

        // DFA check: filter regular sensitive words, queue critical content for audit.
        PostAuditResult audit = sensitiveWordService.checkText(request.getContent());
        comment.setContent(audit.getFilteredContent());
        comment.setStatus(audit.isContainsCritical() ? 2 : 1);

        vibeCommentMapper.insert(comment);

        // Update post comment count
        VibePost post = vibePostMapper.selectById(request.getPostId());
        if (post != null) {
            post.setCommentCount(post.getCommentCount() + 1);
            vibePostMapper.updateById(post);

            // Send notification to post author (if not self-comment)
            if (!post.getUserId().equals(userId)) {
                SysMessage msg = new SysMessage();
                msg.setFromUserId(userId);
                msg.setToUserId(post.getUserId());
                msg.setContent("replied to your post: \"" + post.getTitle() + "\"");
                msg.setType(1);
                msg.setIsRead(0);
                sysMessageMapper.insert(msg);
            }
        }

        // Award core power
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            user.setCorePower(user.getCorePower() + 2);
            sysUserMapper.updateById(user);
        }

        return comment;
    }

    @Override
    public List<VibeComment> getCommentsByPostId(Long postId) {
        return vibeCommentMapper.selectCommentsByPostId(postId);
    }

    @Override
    public int countCommentsByPostId(Long postId) {
        return vibeCommentMapper.countCommentsByPostId(postId);
    }

    @Override
    public boolean deleteComment(Long commentId, Long userId, String role) {
        VibeComment comment = vibeCommentMapper.selectById(commentId);
        if (comment == null) {
            return false;
        }
        boolean isAdmin = "ADMIN".equals(role);
        if (!isAdmin && !comment.getUserId().equals(userId)) {
            throw new IllegalStateException("Only the author or an admin can delete this comment.");
        }
        boolean deleted = vibeCommentMapper.deleteById(commentId) > 0;
        if (deleted && comment.getPostId() != null) {
            vibePostMapper.decrementCommentCount(comment.getPostId());
        }
        return deleted;
    }
}
