package com.nexus.campus.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.campus.entity.PromptVersion;
import com.nexus.campus.mapper.PromptVersionMapper;
import com.nexus.campus.repository.PromptVersionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisPromptVersionRepository implements PromptVersionRepository {

    private final PromptVersionMapper mapper;

    public MyBatisPromptVersionRepository(PromptVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PromptVersion> findByPost(Long postId, String branch) {
        return mapper.selectList(new LambdaQueryWrapper<PromptVersion>()
                .eq(PromptVersion::getPostId, postId)
                .eq(PromptVersion::getBranch, branch)
                .orderByDesc(PromptVersion::getVersion));
    }

    @Override
    public Optional<PromptVersion> find(Long postId, String branch, Integer version) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<PromptVersion>()
                .eq(PromptVersion::getPostId, postId)
                .eq(PromptVersion::getBranch, branch)
                .eq(PromptVersion::getVersion, version)));
    }

    @Override
    public int nextVersion(Long postId, String branch) {
        return mapper.selectMaxVersion(postId, branch) + 1;
    }

    @Override
    public void insert(PromptVersion version) {
        mapper.insert(version);
    }

    @Override
    public long countByPost(Long postId) {
        return mapper.selectVersionCount(postId);
    }

    @Override
    public void deleteByPost(Long postId) {
        mapper.delete(new LambdaQueryWrapper<PromptVersion>().eq(PromptVersion::getPostId, postId));
    }
}
