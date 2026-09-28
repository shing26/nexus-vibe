package com.nexus.campus.repository;

import com.nexus.campus.entity.VibeTag;

import java.util.List;

public interface VibeTagRepository {

    List<VibeTag> findByPostId(Long postId);

    void replaceForPost(Long postId, List<Integer> tagIds);

    void deleteForPost(Long postId);
}
