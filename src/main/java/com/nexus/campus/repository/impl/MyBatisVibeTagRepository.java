package com.nexus.campus.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.campus.entity.VibePostTag;
import com.nexus.campus.entity.VibeTag;
import com.nexus.campus.mapper.VibePostTagMapper;
import com.nexus.campus.mapper.VibeTagMapper;
import com.nexus.campus.repository.VibeTagRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisVibeTagRepository implements VibeTagRepository {

    private final VibeTagMapper tagMapper;
    private final VibePostTagMapper postTagMapper;

    public MyBatisVibeTagRepository(VibeTagMapper tagMapper, VibePostTagMapper postTagMapper) {
        this.tagMapper = tagMapper;
        this.postTagMapper = postTagMapper;
    }

    @Override
    public List<VibeTag> findByPostId(Long postId) {
        return tagMapper.selectTagsByPostId(postId);
    }

    @Override
    public void replaceForPost(Long postId, List<Integer> tagIds) {
        deleteForPost(postId);
        if (tagIds != null && !tagIds.isEmpty()) {
            postTagMapper.insertBatch(postId, tagIds);
        }
    }

    @Override
    public void deleteForPost(Long postId) {
        postTagMapper.delete(new LambdaQueryWrapper<VibePostTag>().eq(VibePostTag::getPostId, postId));
    }
}
