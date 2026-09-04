package com.nexus.campus.service.impl;

import com.nexus.campus.entity.VibeTag;
import com.nexus.campus.mapper.VibeTagMapper;
import com.nexus.campus.service.VibeTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VibeTagServiceImpl implements VibeTagService {

    @Autowired
    private VibeTagMapper vibeTagMapper;

    @Override
    @Cacheable(value = "tags", key = "'active'")
    public List<VibeTag> getActiveTags() {
        return vibeTagMapper.selectActiveTags();
    }

    @Override
    public List<VibeTag> getTagsByPostId(Long postId) {
        return vibeTagMapper.selectTagsByPostId(postId);
    }
}
