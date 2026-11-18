package com.nexus.campus.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.repository.AiReviewLogRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAiReviewLogRepository implements AiReviewLogRepository {

    private final AiReviewLogMapper mapper;

    public MyBatisAiReviewLogRepository(AiReviewLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AiReviewLog log) {
        mapper.insert(log);
    }

    @Override
    public void deleteByPost(Long postId) {
        mapper.delete(new LambdaQueryWrapper<AiReviewLog>().eq(AiReviewLog::getPostId, postId));
    }

    @Override
    public Optional<AiReviewLog> findLatestSafety(Long postId) {
        return Optional.ofNullable(mapper.selectLatestSafetyLogByPostId(postId));
    }

    @Override
    public List<AiReviewLog> findLatestCodeReview(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectLatestCodeReviewByPostIds(postIds);
    }
}
