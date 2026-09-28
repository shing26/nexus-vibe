package com.nexus.campus.service.impl;

import com.nexus.campus.entity.PromptVersion;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.repository.PromptVersionRepository;
import org.springframework.stereotype.Component;

@Component
public class PromptVersionRecorder {

    static final String DEFAULT_BRANCH = "main";

    private final PromptVersionRepository versions;

    public PromptVersionRecorder(PromptVersionRepository versions) {
        this.versions = versions;
    }

    public void record(VibePost post, Long userId, String changeNote) {
        PromptVersion version = new PromptVersion();
        version.setPostId(post.getId());
        version.setVersion(versions.nextVersion(post.getId(), DEFAULT_BRANCH));
        version.setBranch(DEFAULT_BRANCH);
        version.setTitle(post.getTitle());
        version.setContent(post.getContent());
        version.setPromptMetadata(post.getPromptMetadata());
        version.setChangeNote(changeNote);
        version.setCreatedBy(userId);
        versions.insert(version);
    }
}
