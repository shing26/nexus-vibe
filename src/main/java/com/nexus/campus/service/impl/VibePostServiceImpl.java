package com.nexus.campus.service.impl;

import com.nexus.campus.dto.PageResult;
import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.dto.PostVersionVo;
import com.nexus.campus.entity.PromptVersion;
import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.entity.VibeTag;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.metrics.ProductMetrics;
import com.nexus.campus.repository.AiReviewLogRepository;
import com.nexus.campus.repository.PageSlice;
import com.nexus.campus.repository.PromptVersionRepository;
import com.nexus.campus.repository.SysUserRepository;
import com.nexus.campus.repository.VibeCommentRepository;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.repository.VibeTagRepository;
import com.nexus.campus.security.AdminGuard;
import com.nexus.campus.service.PostRankingService;
import com.nexus.campus.service.PostSearchService;
import com.nexus.campus.service.SysMessageService;
import com.nexus.campus.service.VibePostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VibePostServiceImpl implements VibePostService {

    private final PostCreationService postCreationService;
    private final PostEditService postEditService;
    private final PromptVersionRecorder versionRecorder;
    private final VibePostRepository posts;
    private final VibeTagRepository tags;
    private final SysUserRepository users;
    private final PromptVersionRepository versions;
    private final VibeCommentRepository comments;
    private final AiReviewLogRepository reviewLogs;
    private final PostSearchService search;
    private final PostRankingService ranking;
    private final SysMessageService messages;
    private final ProductMetrics productMetrics;

    public VibePostServiceImpl(PostCreationService postCreationService,
                               PostEditService postEditService,
                               PromptVersionRecorder versionRecorder,
                               VibePostRepository posts,
                               VibeTagRepository tags,
                               SysUserRepository users,
                               PromptVersionRepository versions,
                               VibeCommentRepository comments,
                               AiReviewLogRepository reviewLogs,
                               PostSearchService search,
                               PostRankingService ranking,
                               SysMessageService messages,
                               ProductMetrics productMetrics) {
        this.postCreationService = postCreationService;
        this.postEditService = postEditService;
        this.versionRecorder = versionRecorder;
        this.posts = posts;
        this.tags = tags;
        this.users = users;
        this.versions = versions;
        this.comments = comments;
        this.reviewLogs = reviewLogs;
        this.search = search;
        this.ranking = ranking;
        this.messages = messages;
        this.productMetrics = productMetrics;
    }

    @Override
    public VibePost createPost(PostCreateRequest request, Long userId) {
        return postCreationService.createPost(request, userId);
    }

    @Override
    public VibePost updatePost(Long postId, PostUpdateRequest request, Long userId) {
        return postEditService.updatePost(postId, request, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public VibePost forkPrompt(Long postId, Long userId) {
        VibePost source = posts.findById(postId)
                .orElseThrow(() -> BusinessException.notFound("Source template not found."));
        if (!"prompt".equals(source.getPostType())) {
            throw BusinessException.conflict("Only prompt templates can be forked.");
        }
        if (source.getStatus() == null || source.getStatus() != 1) {
            throw BusinessException.conflict("Template is not active.");
        }

        VibePost fork = new VibePost();
        fork.setUserId(userId);
        fork.setCategoryId(source.getCategoryId());
        fork.setTitle(source.getTitle());
        fork.setContent(source.getContent());
        fork.setSummary(source.getSummary());
        fork.setViewCount(0);
        fork.setLikeCount(0);
        fork.setCommentCount(0);
        fork.setStatus(1);
        fork.setPostType("prompt");
        fork.setPromptMetadata(source.getPromptMetadata());
        fork.setForkedFromId(source.getId());
        posts.insert(fork);

        versionRecorder.record(fork, userId, "Forked from post " + source.getId());

        List<Integer> tagIds = tags.findByPostId(postId).stream()
                .map(VibeTag::getId)
                .collect(Collectors.toList());
        tags.replaceForPost(fork.getId(), tagIds);

        users.findById(userId).ifPresent(user -> {
            user.setCorePower(user.getCorePower() + 10);
            users.update(user);
        });
        return fork;
    }

    @Override
    public List<PostVersionVo> getPromptVersions(Long postId) {
        if (posts.findById(postId).isEmpty()) {
            return Collections.emptyList();
        }
        return versions.findByPost(postId, PromptVersionRecorder.DEFAULT_BRANCH).stream()
                .map(version -> {
                    PostVersionVo vo = new PostVersionVo();
                    BeanUtils.copyProperties(version, vo);
                    SysUser author = users.findById(version.getCreatedBy()).orElse(null);
                    vo.setAuthorName(author != null ? author.getNickname() : "Unknown");
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public boolean restorePromptVersion(Long postId, Integer version, Long userId, String changeNote) {
        VibePost post = posts.findById(postId).orElse(null);
        if (post == null) {
            return false;
        }
        SysUser user = users.findById(userId).orElse(null);
        boolean isAdmin = user != null && AdminGuard.isAdmin(user.getRole());
        if (!isAdmin && !post.getUserId().equals(userId)) {
            throw BusinessException.forbidden("Only the author can restore versions.");
        }
        PromptVersion target = versions.find(postId, PromptVersionRecorder.DEFAULT_BRANCH, version)
                .orElse(null);
        if (target == null) {
            return false;
        }

        post.setTitle(target.getTitle());
        post.setContent(target.getContent());
        post.setPromptMetadata(target.getPromptMetadata());
        post.setSummary(PostCreationService.summary(post.getContent()));
        posts.update(post);

        String note = changeNote != null && !changeNote.isBlank()
                ? changeNote.trim() : "Restored from v" + version;
        versionRecorder.record(post, userId, note);
        posts.findWithDetails(postId).ifPresent(search::indexPost);
        return true;
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public boolean deletePost(Long postId, Long userId) {
        VibePost post = posts.findById(postId).orElse(null);
        if (post == null) {
            return false;
        }
        SysUser user = users.findById(userId).orElse(null);
        boolean isAdmin = user != null && AdminGuard.isAdmin(user.getRole());
        if (!isAdmin && !post.getUserId().equals(userId)) {
            throw BusinessException.forbidden("Only the author can delete this post.");
        }

        versions.deleteByPost(postId);
        tags.deleteForPost(postId);
        comments.deleteByPost(postId);
        reviewLogs.deleteByPost(postId);
        posts.delete(postId);
        search.deletePost(postId);
        return true;
    }

    @Override
    public PageResult<PostPageVo> getActivePosts(int page, int size) {
        return getActivePosts(page, size, null);
    }

    @Override
    public PageResult<PostPageVo> getActivePosts(int page, int size, String type) {
        PageSlice<VibePost> slice = posts.pageActive(page, size, null, normalizePostType(type));
        return PageResult.of(page, size, slice.total(), convertToPageVos(slice.records()));
    }

    @Override
    @Deprecated
    public PageResult<PostPageVo> getPostsByCategory(Integer categoryId, int page, int size) {
        return getPostsByCategory(categoryId, page, size, null);
    }

    @Override
    @Deprecated
    public PageResult<PostPageVo> getPostsByCategory(Integer categoryId, int page, int size, String type) {
        PageSlice<VibePost> slice = posts.pageActive(page, size, categoryId, normalizePostType(type));
        return PageResult.of(page, size, slice.total(), convertToPageVos(slice.records()));
    }

    @Override
    @Deprecated
    public PageResult<PostPageVo> searchPosts(String keyword, int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            PageResult<PostPageVo> esResult = search.searchPosts(keyword, page, size);
            if (esResult != null) {
                return esResult;
            }
        }
        PageSlice<VibePost> slice = posts.searchPage(page, size, keyword);
        return PageResult.of(page, size, slice.total(), convertToPageVos(slice.records()));
    }

    @Override
    public PageResult<PostPageVo> filterPosts(int page, int size, String keyword, Integer categoryId,
                                              String language, Integer aiScoreMin, String type, String sort) {
        String normalizedSort = sort == null || sort.isBlank() || "latest".equals(sort) ? "latest" : sort;
        PageSlice<VibePost> slice = posts.filterPage(page, size, keyword, categoryId,
                normalizePostType(type), language, aiScoreMin, normalizedSort);
        return PageResult.of(page, size, slice.total(), convertToPageVos(slice.records()));
    }

    @Override
    public List<PostPageVo> getHotPosts(int limit) {
        return ranking.getHotPosts(limit);
    }

    @Override
    public PostPageVo getPostDetail(Long id) {
        VibePost post = posts.findWithDetails(id).orElse(null);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            return null;
        }
        return convertToPageVo(post);
    }

    @Override
    public boolean incrementView(Long postId) {
        return posts.incrementView(postId);
    }

    @Override
    @Transactional
    public void pinPost(Long postId) {
        VibePost post = posts.findById(postId)
                .orElseThrow(() -> BusinessException.notFound("Post not found."));
        if (post.getStatus() != 1) {
            throw BusinessException.conflict("Only a published post can be pinned.");
        }
        if (posts.pin(postId) <= 0) {
            throw BusinessException.conflict("Post could not be pinned.");
        }
        log.info("Post {} pinned", postId);
    }

    @Override
    @Transactional
    public void unpinPost(Long postId) {
        if (posts.findById(postId).isEmpty()) {
            throw BusinessException.notFound("Post not found.");
        }
        if (posts.unpin(postId) <= 0) {
            throw BusinessException.conflict("Post could not be unpinned.");
        }
        log.info("Post {} unpinned", postId);
    }

    @Override
    public PageResult<PostPageVo> getPostsByUserId(Long userId, int page, int size) {
        PageSlice<VibePost> slice = posts.pageByUser(page, size, userId);
        return PageResult.of(page, size, slice.total(), convertToPageVos(slice.records()));
    }

    @Override
    public List<PostPageVo> getPendingAuditPosts() {
        return posts.findPendingAudit().stream().map(post -> {
            PostPageVo vo = convertToPageVo(post);
            reviewLogs.findLatestSafety(post.getId()).ifPresent(safetyLog -> {
                vo.setSafetyClassification(classifySafetyResult(safetyLog.getResultJson()));
                vo.setSafetySeverity(safetyLog.getSeverity());
                vo.setSafetyIsApproved(safetyLog.getIsApproved());
            });
            return vo;
        }).collect(Collectors.toList());
    }

    private String classifySafetyResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        String lower = resultJson.trim().toLowerCase();
        if (lower.contains("prompt injection")) return "Prompt injection";
        if (lower.contains("harmful")) return "Harmful content";
        if (lower.contains("spam")) return "Spam";
        if (lower.contains("safe")) return "Safe";
        return null;
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public boolean approvePost(Long postId) {
        VibePost post = posts.findById(postId).orElse(null);
        if (post == null) {
            return false;
        }
        post.setStatus(1);
        boolean updated = posts.update(post);
        if (updated) {
            posts.findWithDetails(postId).ifPresent(search::indexPost);
            notifyAuthor(post, "你的帖子《" + post.getTitle() + "》已通过人工审核并发布。");
            productMetrics.recordPostAudited("approve");
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean rejectPost(Long postId) {
        VibePost post = posts.findById(postId).orElse(null);
        if (post == null) {
            return false;
        }
        post.setStatus(3);
        boolean updated = posts.update(post);
        if (updated) {
            notifyAuthor(post, "你的帖子《" + post.getTitle() + "》未通过人工审核，已被下架。如有疑问请联系管理员。");
            productMetrics.recordPostAudited("reject");
        }
        return updated;
    }

    private void notifyAuthor(VibePost post, String content) {
        try {
            messages.sendMessage(SysMessage.FROM_SYSTEM, post.getUserId(), content, SysMessage.TYPE_SYSTEM);
        } catch (Exception e) {
            log.warn("Failed to notify author {} for post {}: {}",
                    post.getUserId(), post.getId(), e.getMessage());
        }
    }

    private List<PostPageVo> convertToPageVos(List<VibePost> posts) {
        return posts.stream().map(this::convertToPageVo).collect(Collectors.toList());
    }

    private PostPageVo convertToPageVo(VibePost post) {
        PostPageVo vo = new PostPageVo();
        BeanUtils.copyProperties(post, vo);
        vo.setVersionCount((int) versions.countByPost(post.getId()));
        List<VibeTag> postTags = tags.findByPostId(post.getId());
        if (postTags != null) {
            vo.setTags(postTags.stream().map(VibeTag::getName).toArray(String[]::new));
        }
        return vo;
    }

    private String normalizePostType(String type) {
        if (type == null || type.isBlank() || "all".equals(type)) {
            return null;
        }
        return type;
    }
}
