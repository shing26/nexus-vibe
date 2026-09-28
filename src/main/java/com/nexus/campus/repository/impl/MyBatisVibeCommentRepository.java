package com.nexus.campus.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.repository.VibeCommentRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisVibeCommentRepository implements VibeCommentRepository {

    private final VibeCommentMapper mapper;

    public MyBatisVibeCommentRepository(VibeCommentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(VibeComment comment) {
        mapper.insert(comment);
    }

    @Override
    public void hideAiReviewComments(Long postId, long reviewerUserId, String marker) {
        mapper.update(null, new LambdaUpdateWrapper<VibeComment>()
                .eq(VibeComment::getPostId, postId)
                .eq(VibeComment::getUserId, reviewerUserId)
                .like(VibeComment::getContent, marker)
                .eq(VibeComment::getStatus, 1)
                .set(VibeComment::getStatus, 0));
    }

    @Override
    public void deleteByPost(Long postId) {
        mapper.delete(new LambdaQueryWrapper<VibeComment>().eq(VibeComment::getPostId, postId));
    }
}
