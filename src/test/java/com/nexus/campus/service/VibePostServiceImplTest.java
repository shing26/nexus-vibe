package com.nexus.campus.service;

import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.PromptVersion;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.mapper.PromptVersionMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.service.impl.VibePostServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class VibePostServiceImplTest {

    @Autowired
    private VibePostService VibePostService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private VibePostMapper VibePostMapper;

    @Autowired
    private PromptVersionMapper promptVersionMapper;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        SysUser user = sysUserMapper.selectById(2L);
        assertNotNull(user, "Seed user testuser (id=2) must exist");
        testUserId = user.getId();
    }

    @Test
    @DisplayName("Create post with clean content -> status = 1 (Active)")
    void createPostWithCleanContent_shouldBeActive() {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("A clean post title");
        request.setContent("This is a perfectly normal post body with no issues.");
        request.setCategoryId(2);
        request.setTags(null);

        VibePost post = VibePostService.createPost(request, testUserId);

        assertNotNull(post.getId());
        assertEquals(1, post.getStatus());
        assertEquals("A clean post title", post.getTitle());
        assertEquals("This is a perfectly normal post body with no issues.", post.getContent());
    }

    @Test
    @DisplayName("Create post with regular sensitive word -> status = 1 (Active) but content filtered")
    void createPostWithSensitiveWord_shouldBeFiltered() {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Safe title");
        request.setContent("This post contains the word shit which should be filtered.");
        request.setCategoryId(2);
        request.setTags(null);

        VibePost post = VibePostService.createPost(request, testUserId);

        assertNotNull(post.getId());
        assertEquals(1, post.getStatus(), "Regular sensitive words should still result in Active status");
        assertFalse(post.getContent().contains("shit"));
    }

    @Test
    @DisplayName("Create prompt template -> version 1 snapshot is written")
    void createPromptTemplate_shouldCreateInitialVersion() {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Versioned Prompt");
        request.setContent("Build a type-safe API client with error handling.");
        request.setCategoryId(2);
        request.setPostType("prompt");
        request.setPromptMetadata("{\"role\":\"senior engineer\",\"recommendedModel\":\"gpt-4o\",\"temperature\":0.6,\"variables\":[\"language\"]}");

        VibePost post = VibePostService.createPost(request, testUserId);

        Long count = promptVersionMapper.selectVersionCount(post.getId());
        assertEquals(1L, count);
        PromptVersion version = promptVersionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PromptVersion>()
                        .eq(PromptVersion::getPostId, post.getId())
                        .eq(PromptVersion::getVersion, 1));
        assertNotNull(version);
        assertEquals("Versioned Prompt", version.getTitle());
    }

    @Test
    @DisplayName("Fork prompt template -> new post with forkedFromId and version 1")
    void forkPrompt_shouldCopyTemplateAndSetSource() {
        VibePost fork = VibePostService.forkPrompt(100L, testUserId);

        assertNotNull(fork.getId());
        assertEquals("prompt", fork.getPostType());
        assertEquals(100L, fork.getForkedFromId());
        assertEquals(1L, promptVersionMapper.selectVersionCount(fork.getId()));
        assertNotNull(fork.getPromptMetadata());
    }

    @Test
    @DisplayName("Fork regular post -> rejected")
    void forkPrompt_onRegularPost_shouldFail() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> VibePostService.forkPrompt(1L, testUserId));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertTrue(error.getMessage().contains("prompt templates"));
    }

    @Test
    @DisplayName("Update prompt template -> new version snapshot is appended")
    void updatePromptTemplate_shouldAppendVersion() {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("Tailwind UI Prompt Architect v2");
        request.setContent("Updated template content.");
        request.setChangeNote("Sharpen constraints");

        VibePost updated = VibePostService.updatePost(101L, request, testUserId);

        assertEquals("Tailwind UI Prompt Architect v2", updated.getTitle());
        assertEquals(2L, promptVersionMapper.selectVersionCount(101L));
    }

    @Test
    @DisplayName("Restore prompt version -> post content rolls back and a new version is appended")
    void restorePromptVersion_shouldRollBackContent() {
        boolean restored = VibePostService.restorePromptVersion(101L, 1, testUserId, null);

        assertTrue(restored);
        VibePost post = VibePostMapper.selectById(101L);
        assertTrue(post.getContent().contains("Design a responsive {{layout}}"));
        assertEquals(2L, promptVersionMapper.selectVersionCount(101L));
    }

    @Test
    @DisplayName("Update regular post -> no version snapshot is written")
    void updateRegularPost_shouldNotCreateVersion() {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("Updated RAG title");
        request.setContent("Updated plain post body.");

        VibePost updated = VibePostService.updatePost(1L, request, testUserId);

        assertEquals("Updated RAG title", updated.getTitle());
        assertEquals(0L, promptVersionMapper.selectVersionCount(1L));
    }

    @Test
    @DisplayName("Update post with critical content -> filtered and routed to pending review")
    void updatePostWithCriticalContent_shouldBePendingReview() {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("Updated title");
        request.setContent("This edit contains 赌博 and must be audited.");

        VibePost updated = VibePostService.updatePost(1L, request, testUserId);

        assertEquals(2, updated.getStatus());
        assertFalse(updated.getContent().contains("赌博"));
        assertEquals(2, VibePostMapper.selectById(1L).getStatus());
    }

    @Test
    @DisplayName("Author can delete their own post and related records are removed")
    void deletePost_byAuthor_shouldSucceed() {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Post to delete");
        request.setContent("This post will be deleted by its author.");
        request.setCategoryId(2);
        request.setTags(null);

        VibePost created = VibePostService.createPost(request, testUserId);

        boolean deleted = VibePostService.deletePost(created.getId(), testUserId);

        assertTrue(deleted);
        assertNull(VibePostMapper.selectById(created.getId()));
        assertEquals(0L, promptVersionMapper.selectVersionCount(created.getId()));
    }

    @Test
    @DisplayName("Other users cannot delete a post they do not own")
    void deletePost_byNonAuthor_shouldBeRejected() {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Protected post");
        request.setContent("Only the author should be able to delete this post.");
        request.setCategoryId(2);
        request.setTags(null);

        VibePost created = VibePostService.createPost(request, testUserId);

        BusinessException error = assertThrows(BusinessException.class,
                () -> VibePostService.deletePost(created.getId(), 3L));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertTrue(error.getMessage().contains("author"));
        assertNotNull(VibePostMapper.selectById(created.getId()));
    }

    @Test
    @DisplayName("Deleting a nonexistent post returns false")
    void deletePost_missingPost_shouldReturnFalse() {
        assertFalse(VibePostService.deletePost(999999999L, testUserId));
    }

    @Test
    @DisplayName("Unanswered posts accept a channel filter; null keeps the site-wide list")
    void getUnansweredPosts_channelScopedFilter() {
        // Rows are written through the mapper, not createPost: the moderation
        // pipeline that runs on created posts fail-closes to pending-review when
        // the LLM is unreachable (the test environment's normal state), and an
        // asynchronously held post would make this read race the pipeline.
        VibePost workshopPost = new VibePost();
        workshopPost.setUserId(testUserId);
        workshopPost.setCategoryId(2);
        workshopPost.setTitle("Unanswered channel-scope workshop post");
        workshopPost.setContent("Nobody has replied to this workshop post yet.");
        workshopPost.setStatus(1);
        workshopPost.setCommentCount(0);
        workshopPost.setCreateTime(java.time.LocalDateTime.now());
        VibePostMapper.insert(workshopPost);

        VibePost showcasePost = new VibePost();
        showcasePost.setUserId(testUserId);
        showcasePost.setCategoryId(3);
        showcasePost.setTitle("Unanswered channel-scope showcase post");
        showcasePost.setContent("Nobody has replied to this showcase post yet.");
        showcasePost.setStatus(1);
        showcasePost.setCommentCount(0);
        showcasePost.setCreateTime(java.time.LocalDateTime.now());
        VibePostMapper.insert(showcasePost);

        // Site-wide (channelId absent): both fresh unanswered posts are visible —
        // the pre-existing behaviour, unchanged by the new parameter.
        List<com.nexus.campus.dto.PostPageVo> siteWide = VibePostService.getUnansweredPosts(20, null);
        assertTrue(siteWide.stream().anyMatch(p -> workshopPost.getId().equals(p.getId())));
        assertTrue(siteWide.stream().anyMatch(p -> showcasePost.getId().equals(p.getId())));

        // Scoped to the workshop channel: only that channel's rows survive.
        List<com.nexus.campus.dto.PostPageVo> workshop = VibePostService.getUnansweredPosts(20, 2L);
        assertTrue(workshop.stream().anyMatch(p -> workshopPost.getId().equals(p.getId())));
        assertFalse(workshop.stream().anyMatch(p -> showcasePost.getId().equals(p.getId())));
        assertTrue(workshop.stream().allMatch(p -> p.getCategoryId() != null && p.getCategoryId() == 2));

        // Scoped to the showcase channel: the mirror image.
        List<com.nexus.campus.dto.PostPageVo> showcase = VibePostService.getUnansweredPosts(20, 3L);
        assertTrue(showcase.stream().anyMatch(p -> showcasePost.getId().equals(p.getId())));
        assertFalse(showcase.stream().anyMatch(p -> workshopPost.getId().equals(p.getId())));
    }
}
