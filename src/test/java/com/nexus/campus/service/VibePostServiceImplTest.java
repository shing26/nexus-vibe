package com.nexus.campus.service;

import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.PromptVersion;
import com.nexus.campus.mapper.PromptVersionMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.service.impl.VibePostServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

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
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> VibePostService.forkPrompt(1L, testUserId));
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

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> VibePostService.deletePost(created.getId(), 3L));
        assertTrue(error.getMessage().contains("author"));
        assertNotNull(VibePostMapper.selectById(created.getId()));
    }

    @Test
    @DisplayName("Deleting a nonexistent post returns false")
    void deletePost_missingPost_shouldReturnFalse() {
        assertFalse(VibePostService.deletePost(999999999L, testUserId));
    }
}
