package com.nexus.campus.service.impl;

import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.entity.Channel;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.PostStatus;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.metrics.ProductMetrics;
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
public class PostCreationService {

    private final VibePostRepository posts;
    private final SysUserRepository users;
    private final ChannelRepository channels;
    private final VibeTagRepository tags;
    private final PostSearchService search;
    private final PromptVersionRecorder versions;
    private final ProductMetrics productMetrics;
    private final PostPublishGate publishGate;

    public PostCreationService(VibePostRepository posts,
                               SysUserRepository users,
                               ChannelRepository channels,
                               VibeTagRepository tags,
                               PostSearchService search,
                               PromptVersionRecorder versions,
                               ProductMetrics productMetrics,
                               PostPublishGate publishGate) {
        this.posts = posts;
        this.users = users;
        this.channels = channels;
        this.tags = tags;
        this.search = search;
        this.versions = versions;
        this.productMetrics = productMetrics;
        this.publishGate = publishGate;
    }

    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public VibePost createPost(PostCreateRequest request, Long userId) {
        VibePost post = new VibePost();
        post.setUserId(userId);
        post.setCategoryId(request.getCategoryId());
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setPostType(request.getPostType() != null ? request.getPostType() : "post");
        post.setPromptMetadata(request.getPromptMetadata());

        PostPublishGate.AuditVerdict verdict = publishGate.applySensitiveWordPolicy(post);
        post.setStatus(verdict.critical()
                ? PostStatus.PENDING_REVIEW.getCode() : PostStatus.ACTIVE.getCode());
        post.setSummary(summary(post.getContent()));

        Channel channel = channels.findById(request.getCategoryId()).orElse(null);
        if (channel != null && "announcements".equals(channel.getSlug())) {
            SysUser user = users.findById(userId).orElse(null);
            if (user == null || !AdminGuard.isAdmin(user.getRole())) {
                throw BusinessException.forbidden("Only admins can post in the announcements channel.");
            }
        }

        posts.insert(post);

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            tags.replaceForPost(post.getId(), request.getTags());
        }
        if ("prompt".equals(post.getPostType())) {
            versions.record(post, userId, "Initial version");
        }

        SysUser user = users.findById(userId).orElse(null);
        post.setAuthorName(user != null ? user.getNickname() : "");
        post.setCategoryName(channels.findById(post.getCategoryId())
                .map(Channel::getName).orElse(""));
        search.indexPost(post);

        if (user != null) {
            int reward = post.getStatus() == PostStatus.ACTIVE.getCode() ? 10 : 3;
            user.setCorePower(user.getCorePower() + reward);
            users.update(user);
        }

        publishGate.dispatchForNewPost(post, userId);

        productMetrics.recordPostCreated(post.getStatus());
        return post;
    }

    static String summary(String content) {
        String plain = content.replaceAll("<[^>]*>", "");
        return plain.length() > 200 ? plain.substring(0, 200) + "..." : plain;
    }
}
