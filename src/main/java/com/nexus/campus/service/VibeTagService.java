package com.nexus.campus.service;

import com.nexus.campus.dto.TagUsageVo;
import com.nexus.campus.entity.VibeTag;

import java.util.List;

public interface VibeTagService {

    List<VibeTag> getActiveTags();

    List<TagUsageVo> getActiveTagsWithUsage();

    List<VibeTag> getTagsByPostId(Long postId);
}
