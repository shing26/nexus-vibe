package com.nexus.campus.service.impl;

import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.entity.Channel;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.PostStatus;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.repository.ChannelRepository;
import com.nexus.campus.repository.SysUserRepository;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.repository.VibeTagRepository;
import com.nexus.campus.security.AdminGuard;
import com.nexus.campus.service.PostSearchService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostEditService {

    private final VibePostRepository posts;
    private final SysUserRepository users;
    private final ChannelRepository channels;
    private final VibeTagRepository tags;
    private final PostSearchService search;
    private final PromptVersionRecorder versions;
    private final PostPublishGate publishGate;

    public PostEditService(VibePostRepository posts,
                           SysUserRepository users,
                           ChannelRepository channels,
                           VibeTagRepository tags,
                           PostSearchService search,
                           PromptVersionRecorder versions,
                           PostPublishGate publishGate) {
        this.posts = posts;
        this.users = users;
        this.channels = channels;
        this.tags = tags;
        this.search = search;
        this.versions = versions;
        this.publishGate = publishGate;
    }

    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public VibePost updatePost(Long postId, PostUpdateRequest request, Long userId) {
        VibePost post = posts.findById(postId)
                .orElseThrow(() -> BusinessException.notFound("Post not found."));
        SysUser user = users.findById(userId).orElse(null);
        boolean isAdmin = user != null && AdminGuard.isAdmin(user.getRole());
        if (!isAdmin && !post.getUserId().equals(userId)) {
            throw BusinessException.forbidden("Only the author can edit this post.");
        }

        String previousContent = post.getContent();
        String previousTitle = post.getTitle();
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            post.setTitle(request.getTitle().trim());
        }
        if (request.getCategoryId() != null) {
            Channel channel = channels.findById(request.getCategoryId())
                    .orElseThrow(() -> BusinessException.notFound("Channel not found."));
            if ("announcements".equals(channel.getSlug()) && !isAdmin) {
                throw BusinessException.forbidden("Only admins can post in the announcements channel.");
            }
            post.setCategoryId(request.getCategoryId());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getPostType() != null) {
            post.setPostType(request.getPostType());
        }
        if (request.getPromptMetadata() != null) {
            post.setPromptMetadata(request.getPromptMetadata());
        }

        boolean titleChanged = request.getTitle() != null
                && !request.getTitle().isBlank()
                && !request.getTitle().trim().equals(previousTitle);
        boolean contentChanged = request.getContent() != null
                && !request.getContent().equals(previousContent);

        if (titleChanged || contentChanged) {
            PostPublishGate.AuditVerdict verdict = publishGate.applySensitiveWordPolicy(post);
            // A clean edit never moves the status: an already-rejected or
            // pending post stays where it is (see
            // VibePostEditAuditTest#cleanEditDoesNotReactivateRejectedPost).
            if (verdict.critical()) {
                post.setStatus(PostStatus.PENDING_REVIEW.getCode());
            }
        }

        post.setSummary(PostCreationService.summary(post.getContent()));

        if (request.getTags() != null) {
            tags.replaceForPost(postId, request.getTags());
        }

        posts.update(post);

        if ("prompt".equals(post.getPostType())) {
            String note = request.getChangeNote() != null && !request.getChangeNote().isBlank()
                    ? request.getChangeNote().trim() : "Updated via editor";
            versions.record(post, userId, note);
        }

        posts.findWithDetails(postId).ifPresent(search::indexPost);

        publishGate.dispatchForEditedPost(post, userId, titleChanged, contentChanged);
        return post;
    }
}
