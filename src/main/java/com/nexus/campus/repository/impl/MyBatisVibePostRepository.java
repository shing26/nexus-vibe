package com.nexus.campus.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.repository.PageSlice;
import com.nexus.campus.repository.VibePostRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisVibePostRepository implements VibePostRepository {

    private final VibePostMapper mapper;

    public MyBatisVibePostRepository(VibePostMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<VibePost> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<VibePost> findWithDetails(Long id) {
        return Optional.ofNullable(mapper.selectPostWithDetails(id));
    }

    @Override
    public void insert(VibePost post) {
        mapper.insert(post);
    }

    @Override
    public boolean update(VibePost post) {
        return mapper.updateById(post) > 0;
    }

    @Override
    public boolean updateStatus(Long id, int status) {
        return mapper.updatePostStatus(id, status) > 0;
    }

    @Override
    public boolean delete(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public PageSlice<VibePost> pageActive(int page, int size, Integer categoryId, String postType) {
        return slice(mapper.selectPostPage(new Page<>(page, size), categoryId, postType));
    }

    @Override
    public PageSlice<VibePost> searchPage(int page, int size, String keyword) {
        return slice(mapper.selectSearchPage(new Page<>(page, size), keyword));
    }

    @Override
    public PageSlice<VibePost> filterPage(int page, int size, String keyword, Integer categoryId,
                                          String postType, String language, Integer aiScoreMin,
                                          String sort) {
        return slice(mapper.selectFilteredPage(new Page<>(page, size), keyword, categoryId,
                postType, language, aiScoreMin, sort));
    }

    @Override
    public PageSlice<VibePost> pageByUser(int page, int size, Long userId) {
        return slice(mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<VibePost>()
                .eq(VibePost::getUserId, userId)
                .eq(VibePost::getStatus, 1)
                .orderByDesc(VibePost::getCreateTime)));
    }

    @Override
    public List<VibePost> findPendingAudit() {
        return mapper.selectPendingAuditPosts();
    }

    @Override
    public boolean incrementView(Long id) {
        return mapper.incrementViewCount(id) > 0;
    }

    @Override
    public int recalculateCommentCount(Long postId) {
        return mapper.recalculateCommentCount(postId);
    }

    @Override
    public int pin(Long id) {
        return mapper.pinPost(id);
    }

    @Override
    public int unpin(Long id) {
        return mapper.unpinPost(id);
    }

    /** The one place a MyBatis page becomes an application-layer slice. */
    private static PageSlice<VibePost> slice(Page<VibePost> page) {
        return new PageSlice<>(page.getRecords(), page.getTotal());
    }
}
