package com.nexus.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.dto.PageResult;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.dto.PostVersionVo;
import com.nexus.campus.entity.*;
import com.nexus.campus.mapper.*;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.service.VibePostService;
import com.nexus.campus.agent.AiReviewEvent;
import com.nexus.campus.agent.AiSafetyCheckEvent;
import com.nexus.campus.service.PostSearchService;
import com.nexus.campus.service.PostRankingService;
import com.nexus.campus.service.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VibePostServiceImpl implements VibePostService {
    private static final String DEFAULT_BRANCH = "main";

    private void applyTypeFilter(LambdaQueryWrapper<VibePost> queryWrapper, String type) {
        if (type != null && !"all".equals(type)) {
            queryWrapper.eq(VibePost::getPostType, type);
        } else if (type == null) {
            queryWrapper.eq(VibePost::getPostType, "post"); // default: only regular posts
        }
    }

    @Autowired
    private PostSearchService postSearchService;

    @Autowired
    private PostRankingService postRankingService;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private VibePostTagMapper vibePostTagMapper;

    @Autowired
    private VibeTagMapper vibeTagMapper;

    @Autowired
    private ChannelMapper channelMapper;

    @Autowired
    private VibeCommentMapper vibeCommentMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

    @Autowired
    private PromptVersionMapper promptVersionMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

   @Value("${campus.ai.review.enabled:true}")
   private boolean aiReviewEnabled;

   @Override
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

        // Set post type and prompt metadata
        post.setPostType(request.getPostType() != null ? request.getPostType() : "post");
        post.setPromptMetadata(request.getPromptMetadata());

        // DFA audit (SensitiveWordService)
        PostAuditResult titleAudit   = sensitiveWordService.checkText(request.getTitle());
        PostAuditResult contentAudit = sensitiveWordService.checkText(request.getContent());
        boolean anyCritical  = titleAudit.isContainsCritical() || contentAudit.isContainsCritical();
        boolean anySensitive = titleAudit.isContainsSensitive() || contentAudit.isContainsSensitive();
        post.setStatus(anyCritical ? 2 : 1);
        if (anySensitive) {
            post.setTitle(titleAudit.getFilteredContent());
            post.setContent(contentAudit.getFilteredContent());
        }
        // Generate summary from the (now-filtered) content
        String filteredPlain = post.getContent().replaceAll("<[^>]*>", "");
       post.setSummary(filteredPlain.length() > 200 ? filteredPlain.substring(0, 200) + "..." : filteredPlain);

        // Announcement channel: admin-only guard
        Channel channel = channelMapper.selectById(request.getCategoryId());
        if (channel != null && "announcements".equals(channel.getSlug())) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || !"ADMIN".equals(user.getRole())) {
                throw new IllegalArgumentException("只有管理员才能在公告频道发帖");
            }
        }

        vibePostMapper.insert(post);

        // Link tags
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            vibePostTagMapper.insertBatch(post.getId(), request.getTags());
        }

        // Prompt templates get an initial immutable version snapshot
        if ("prompt".equals(post.getPostType())) {
            saveVersionSnapshot(post, userId, "Initial version");
        }

        // Fetch user for author name and core power award
        SysUser user = sysUserMapper.selectById(userId);

        // Index in Elasticsearch
        post.setAuthorName(user != null ? user.getNickname() : "");
        Channel category = channelMapper.selectById(post.getCategoryId());
        post.setCategoryName(category != null ? category.getName() : "");
        postSearchService.indexPost(post);

        // Award core power for posting
        if (user != null) {
            int reward = post.getStatus() == 1 ? 10 : 3;
            user.setCorePower(user.getCorePower() + reward);
            sysUserMapper.updateById(user);
        }

        // Publish AI review event if enabled
        if (aiReviewEnabled) {
            eventPublisher.publishEvent(new AiReviewEvent(this, post.getId(), post.getContent()));
        }

        // Publish AI safety check event if enabled (only for posts that passed DFA)
        if (aiReviewEnabled && post.getStatus() == 1) {
            eventPublisher.publishEvent(new AiSafetyCheckEvent(this, post.getId(), post.getContent(), userId));
        }

        return post;
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public VibePost updatePost(Long postId, PostUpdateRequest request, Long userId) {
        VibePost post = vibePostMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("Post not found.");
        }
        SysUser user = sysUserMapper.selectById(userId);
        boolean isAdmin = user != null && "ADMIN".equals(user.getRole());
        if (!isAdmin && !post.getUserId().equals(userId)) {
            throw new IllegalStateException("Only the author can edit this post.");
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            post.setTitle(request.getTitle().trim());
        }
        if (request.getCategoryId() != null) {
            Channel channel = channelMapper.selectById(request.getCategoryId());
            if (channel == null) {
                throw new IllegalArgumentException("Channel not found.");
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

        String plain = post.getContent().replaceAll("<[^>]*>", "");
        post.setSummary(plain.length() > 200 ? plain.substring(0, 200) + "..." : plain);

        if (request.getTags() != null) {
            vibePostTagMapper.delete(new LambdaQueryWrapper<VibePostTag>().eq(VibePostTag::getPostId, postId));
            if (!request.getTags().isEmpty()) {
                vibePostTagMapper.insertBatch(postId, request.getTags());
            }
        }

        vibePostMapper.updateById(post);

        if ("prompt".equals(post.getPostType())) {
            String note = request.getChangeNote() != null && !request.getChangeNote().isBlank()
                    ? request.getChangeNote().trim() : "Updated via editor";
            saveVersionSnapshot(post, userId, note);
        }

        VibePost fullPost = vibePostMapper.selectPostWithDetails(postId);
        if (fullPost != null) {
            postSearchService.indexPost(fullPost);
        }
        return post;
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public VibePost forkPrompt(Long postId, Long userId) {
        VibePost source = vibePostMapper.selectById(postId);
        if (source == null) {
            throw new IllegalArgumentException("Source template not found.");
        }
        if (!"prompt".equals(source.getPostType())) {
            throw new IllegalArgumentException("Only prompt templates can be forked.");
        }
        if (source.getStatus() == null || source.getStatus() != 1) {
            throw new IllegalArgumentException("Template is not active.");
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
        vibePostMapper.insert(fork);

        saveVersionSnapshot(fork, userId, "Forked from post " + source.getId());

        List<VibeTag> tags = vibeTagMapper.selectTagsByPostId(postId);
        if (tags != null && !tags.isEmpty()) {
            List<Integer> tagIds = tags.stream().map(VibeTag::getId).collect(Collectors.toList());
            vibePostTagMapper.insertBatch(fork.getId(), tagIds);
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            user.setCorePower(user.getCorePower() + 10);
            sysUserMapper.updateById(user);
        }
        return fork;
    }

    @Override
    public List<PostVersionVo> getPromptVersions(Long postId) {
        VibePost post = vibePostMapper.selectById(postId);
        if (post == null) {
            return Collections.emptyList();
        }
        List<PromptVersion> versions = promptVersionMapper.selectList(
                new LambdaQueryWrapper<PromptVersion>()
                        .eq(PromptVersion::getPostId, postId)
                        .eq(PromptVersion::getBranch, DEFAULT_BRANCH)
                        .orderByDesc(PromptVersion::getVersion));
        return versions.stream().map(version -> {
            PostVersionVo vo = new PostVersionVo();
            BeanUtils.copyProperties(version, vo);
            SysUser author = sysUserMapper.selectById(version.getCreatedBy());
            vo.setAuthorName(author != null ? author.getNickname() : "Unknown");
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public boolean restorePromptVersion(Long postId, Integer version, Long userId, String changeNote) {
        VibePost post = vibePostMapper.selectById(postId);
        if (post == null) {
            return false;
        }
        SysUser user = sysUserMapper.selectById(userId);
        boolean isAdmin = user != null && "ADMIN".equals(user.getRole());
        if (!isAdmin && !post.getUserId().equals(userId)) {
            throw new IllegalStateException("Only the author can restore versions.");
        }
        PromptVersion target = promptVersionMapper.selectOne(
                new LambdaQueryWrapper<PromptVersion>()
                        .eq(PromptVersion::getPostId, postId)
                        .eq(PromptVersion::getBranch, DEFAULT_BRANCH)
                        .eq(PromptVersion::getVersion, version));
        if (target == null) {
            return false;
        }

        post.setTitle(target.getTitle());
        post.setContent(target.getContent());
        post.setPromptMetadata(target.getPromptMetadata());
        String plain = post.getContent().replaceAll("<[^>]*>", "");
        post.setSummary(plain.length() > 200 ? plain.substring(0, 200) + "..." : plain);
        vibePostMapper.updateById(post);

        String note = changeNote != null && !changeNote.isBlank()
                ? changeNote.trim() : "Restored from v" + version;
        saveVersionSnapshot(post, userId, note);

        VibePost fullPost = vibePostMapper.selectPostWithDetails(postId);
        if (fullPost != null) {
            postSearchService.indexPost(fullPost);
        }
        return true;
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public boolean deletePost(Long postId, Long userId) {
        VibePost post = vibePostMapper.selectById(postId);
        if (post == null) {
            return false;
        }
        SysUser user = sysUserMapper.selectById(userId);
        boolean isAdmin = user != null && "ADMIN".equals(user.getRole());
        if (!isAdmin && !post.getUserId().equals(userId)) {
            throw new IllegalStateException("Only the author can delete this post.");
        }

        promptVersionMapper.delete(new LambdaQueryWrapper<PromptVersion>().eq(PromptVersion::getPostId, postId));
        vibePostTagMapper.delete(new LambdaQueryWrapper<VibePostTag>().eq(VibePostTag::getPostId, postId));
        vibeCommentMapper.delete(new LambdaQueryWrapper<VibeComment>().eq(VibeComment::getPostId, postId));
        aiReviewLogMapper.delete(new LambdaQueryWrapper<AiReviewLog>().eq(AiReviewLog::getPostId, postId));
        vibePostMapper.deleteById(postId);
        postSearchService.deletePost(postId);
        return true;
    }

    private void saveVersionSnapshot(VibePost post, Long userId, String changeNote) {
        PromptVersion version = new PromptVersion();
        version.setPostId(post.getId());
        version.setVersion(promptVersionMapper.selectMaxVersion(post.getId(), DEFAULT_BRANCH) + 1);
        version.setBranch(DEFAULT_BRANCH);
        version.setTitle(post.getTitle());
        version.setContent(post.getContent());
        version.setPromptMetadata(post.getPromptMetadata());
        version.setChangeNote(changeNote);
        version.setCreatedBy(userId);
        promptVersionMapper.insert(version);
    }

    @Override
    public PageResult<PostPageVo> getActivePosts(int page, int size) {
        return getActivePosts(page, size, null);
    }

    @Override
    public PageResult<PostPageVo> getActivePosts(int page, int size, String type) {
        Page<VibePost> mpPage = vibePostMapper.selectPostPage(
                new Page<>(page, size),
                null,
                "all".equals(type) ? null : (type == null || type.isBlank() ? "post" : type)
        );
        List<PostPageVo> vos = convertToPageVos(mpPage.getRecords());
        return PageResult.of(page, size, mpPage.getTotal(), vos);
    }

    @Override
    @Deprecated
    public PageResult<PostPageVo> getPostsByCategory(Integer categoryId, int page, int size) {
        return getPostsByCategory(categoryId, page, size, null);
    }

    @Override
    @Deprecated
    public PageResult<PostPageVo> getPostsByCategory(Integer categoryId, int page, int size, String type) {
        Page<VibePost> mpPage = vibePostMapper.selectPostPage(
                new Page<>(page, size),
                categoryId,
                "all".equals(type) ? null : (type == null || type.isBlank() ? "post" : type)
        );
        List<PostPageVo> vos = convertToPageVos(mpPage.getRecords());
        return PageResult.of(page, size, mpPage.getTotal(), vos);
    }

    @Override
    @Deprecated
    public PageResult<PostPageVo> searchPosts(String keyword, int page, int size) {
        // Try ES first
        if (keyword != null && !keyword.isBlank()) {
            PageResult<PostPageVo> esResult = postSearchService.searchPosts(keyword, page, size);
            if (esResult != null) {
                return esResult;
            }
        }
        // Fallback to MySQL LIKE query with pagination
        Page<VibePost> mpPage = vibePostMapper.selectSearchPage(
                new Page<>(page, size),
                keyword
        );
        List<PostPageVo> vos = convertToPageVos(mpPage.getRecords());
        return PageResult.of(page, size, mpPage.getTotal(), vos);
    }

    @Override
    public PageResult<PostPageVo> filterPosts(int page, int size, String keyword, Integer categoryId,
                                              String language, Integer aiScoreMin, String type, String sort) {
        String normalizedType = "all".equals(type) ? null : (type == null || type.isBlank() ? "post" : type);
        String normalizedSort = sort == null || sort.isBlank() || "latest".equals(sort) ? "latest" : sort;
        Page<VibePost> mpPage = vibePostMapper.selectFilteredPage(
                new Page<>(page, size),
                keyword,
                categoryId,
                normalizedType,
                language,
                aiScoreMin,
                normalizedSort
        );
        List<PostPageVo> vos = convertToPageVos(mpPage.getRecords());
        return PageResult.of(page, size, mpPage.getTotal(), vos);
    }

    @Override
    public List<PostPageVo> getHotPosts(int limit) {
        return postRankingService.getHotPosts(limit);
    }

    @Override
    public PostPageVo getPostDetail(Long id) {
        VibePost post = vibePostMapper.selectPostWithDetails(id);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) return null;
        return convertToPageVo(post);
    }

    @Override
    @Deprecated
    public VibePost likePost(Long postId) {
        vibePostMapper.incrementLikeCount(postId);
        VibePost post = vibePostMapper.selectById(postId);
        // Notify ranking service
        if (post != null) {
            postRankingService.onLike(postId, post.getLikeCount());
        }
        return post;
    }

    @Override
    public boolean incrementView(Long postId) {
        return vibePostMapper.incrementViewCount(postId) > 0;
    }

    @Override
    @Transactional
    public boolean pinPost(Long postId) {
        VibePost post = vibePostMapper.selectById(postId);
        if (post == null || post.getStatus() != 1) return false;
        int rows = vibePostMapper.pinPost(postId);
        if (rows > 0) {
            log.info("Post {} pinned", postId);
        }
        return rows > 0;
    }

    @Override
    @Transactional
    public boolean unpinPost(Long postId) {
        VibePost post = vibePostMapper.selectById(postId);
        if (post == null) return false;
        int rows = vibePostMapper.unpinPost(postId);
        if (rows > 0) {
            log.info("Post {} unpinned", postId);
        }
        return rows > 0;
    }

    @Override
    public PageResult<PostPageVo> getPostsByUserId(Long userId, int page, int size) {
        Page<VibePost> mpPage = vibePostMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<VibePost>()
                        .eq(VibePost::getUserId, userId)
                        .eq(VibePost::getStatus, 1)
                        .orderByDesc(VibePost::getCreateTime)
        );
        List<PostPageVo> vos = convertToPageVos(mpPage.getRecords());
        return PageResult.of(page, size, mpPage.getTotal(), vos);
    }

    @Override
    public List<PostPageVo> getPendingAuditPosts() {
        List<VibePost> posts = vibePostMapper.selectPendingAuditPosts();
        return posts.stream().map(post -> {
            PostPageVo vo = convertToPageVo(post);
            // Attach latest safety check result
            AiReviewLog safetyLog = aiReviewLogMapper.selectLatestSafetyLogByPostId(post.getId());
            if (safetyLog != null) {
                String classification = classifySafetyResult(safetyLog.getResultJson());
                vo.setSafetyClassification(classification);
                vo.setSafetySeverity(safetyLog.getSeverity());
                vo.setSafetyIsApproved(safetyLog.getIsApproved());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * Normalise the raw LLM response from the safety check into a display label.
     * The resultJson contains the raw LLM response string.
     */
    private String classifySafetyResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) return null;
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
        VibePost post = vibePostMapper.selectById(postId);
        if (post == null) return false;
        post.setStatus(1);
        boolean updated = vibePostMapper.updateById(post) > 0;
        if (updated) {
            // Re-index with approved status
            VibePost fullPost = vibePostMapper.selectPostWithDetails(postId);
            if (fullPost != null) {
                postSearchService.indexPost(fullPost);
            }
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean rejectPost(Long postId) {
        VibePost post = vibePostMapper.selectById(postId);
        if (post == null) return false;
        post.setStatus(3); // 3 = Rejected
        return vibePostMapper.updateById(post) > 0;
    }

    @Cacheable(value = "posts", key = "'active'")
    public List<PostPageVo> getActivePostsLegacy() {
        List<VibePost> posts = vibePostMapper.selectActivePosts();
        return convertToPageVos(posts);
    }

    private List<PostPageVo> convertToPageVos(List<VibePost> posts) {
        return posts.stream().map(this::convertToPageVo).collect(Collectors.toList());
    }

    private PostPageVo convertToPageVo(VibePost post) {
        PostPageVo vo = new PostPageVo();
        BeanUtils.copyProperties(post, vo);
        vo.setVersionCount((int) promptVersionMapper.selectVersionCount(post.getId()));

        // Attach tags
        List<VibeTag> tags = vibeTagMapper.selectTagsByPostId(post.getId());
        if (tags != null) {
            vo.setTags(tags.stream().map(VibeTag::getName).toArray(String[]::new));
        }
        return vo;
    }
}
