package com.nexus.campus.repository;

import com.nexus.campus.entity.PromptVersion;

import java.util.List;
import java.util.Optional;

public interface PromptVersionRepository {

    List<PromptVersion> findByPost(Long postId, String branch);

    Optional<PromptVersion> find(Long postId, String branch, Integer version);

    int nextVersion(Long postId, String branch);

    void insert(PromptVersion version);

    long countByPost(Long postId);

    void deleteByPost(Long postId);
}
