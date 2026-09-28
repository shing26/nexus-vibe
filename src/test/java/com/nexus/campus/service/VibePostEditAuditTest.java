package com.nexus.campus.service;

import com.nexus.campus.agent.ReviewPolicy;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.metrics.ProductMetrics;
import com.nexus.campus.repository.ChannelRepository;
import com.nexus.campus.repository.SysUserRepository;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.repository.VibeTagRepository;
import com.nexus.campus.service.impl.PostAgentEventPublisher;
import com.nexus.campus.service.impl.PostCreationService;
import com.nexus.campus.service.impl.PostEditService;
import com.nexus.campus.service.impl.PostPublishGate;
import com.nexus.campus.service.impl.PromptVersionRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Creation and editing both treat a visible-content change as a new
 * submission: DFA re-check, review dispatch, and safety dispatch. These
 * behaviours moved out of the post service facade into dedicated application
 * services, so the tests drive those services directly over the repository
 * boundary.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VibePostEditAuditTest {

    @Mock
    private VibePostRepository posts;
    @Mock
    private VibeTagRepository tags;
    @Mock
    private ChannelRepository channels;
    @Mock
    private SysUserRepository users;
    @Mock
    private SensitiveWordService sensitiveWordService;
    @Mock
    private PromptVersionRecorder versionRecorder;
    @Mock
    private PostAgentEventPublisher agentEvents;
    @Mock
    private ReviewPolicy reviewPolicy;
    @Mock
    private PostSearchService postSearchService;
    @Mock
    private ProductMetrics productMetrics;

    private PostCreationService creationService;
    private PostEditService editService;
    private PostPublishGate publishGate;
    private CampusAiProperties aiProperties;

    private VibePost post;

    @BeforeEach
    void setUp() {
        aiProperties = new CampusAiProperties();
        publishGate = new PostPublishGate(sensitiveWordService, posts, agentEvents, reviewPolicy, aiProperties);
        creationService = new PostCreationService(posts, users, channels, tags,
                postSearchService, versionRecorder, productMetrics, publishGate);
        editService = new PostEditService(posts, users, channels, tags,
                postSearchService, versionRecorder, publishGate);
        setAiFlags(true, true);

        post = new VibePost();
        post.setId(7L);
        post.setUserId(2L);
        post.setTitle("Old title");
        post.setContent("Old body");
        post.setPostType("post");
        post.setStatus(1);
        when(posts.findById(7L)).thenReturn(Optional.of(post));
        SysUser user = new SysUser();
        user.setId(2L);
        user.setRole("USER");
        user.setCorePower(0);
        when(users.findById(2L)).thenReturn(Optional.of(user));
        when(posts.update(any(VibePost.class))).thenReturn(true);
        lenient().when(reviewPolicy.shouldReview(any(VibePost.class))).thenReturn(true);
    }

    private void setAiFlags(boolean review, boolean safety) {
        aiProperties.getReview().setEnabled(review);
        aiProperties.getSafety().setEnabled(safety);
    }

    @Test
    @DisplayName("Editing visible content re-runs DFA, review, and safety dispatch")
    void contentEditIsTreatedAsANewSubmission() {
        when(sensitiveWordService.checkText("New title")).thenReturn(PostAuditResult.pass("New title"));
        when(sensitiveWordService.checkText("New body")).thenReturn(PostAuditResult.pass("New body"));

        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("New title");
        request.setContent("New body");

        editService.updatePost(7L, request, 2L);

        verify(sensitiveWordService).checkText("New title");
        verify(sensitiveWordService).checkText("New body");
        verify(agentEvents).publishReview(eq(post), eq(2L));
        verify(agentEvents).publishSafety(eq(post), eq(2L));
    }

    @Test
    @DisplayName("Editing a rejected post cleanly does not silently reactivate it")
    void cleanEditDoesNotReactivateRejectedPost() {
        post.setStatus(3);
        setAiFlags(false, true);
        when(sensitiveWordService.checkText("New title")).thenReturn(PostAuditResult.pass("New title"));
        when(sensitiveWordService.checkText("New body")).thenReturn(PostAuditResult.pass("New body"));

        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("New title");
        request.setContent("New body");

        VibePost updated = editService.updatePost(7L, request, 2L);

        assertEquals(3, updated.getStatus());
        verify(agentEvents, never()).publishReview(any(), any());
        verify(agentEvents, never()).publishSafety(any(), any());
    }

    @Test
    @DisplayName("An edit without a code block never enters REVIEWING")
    void editWithoutCodeBlockDoesNotEnterReviewing() {
        when(sensitiveWordService.checkText("New title")).thenReturn(PostAuditResult.pass("New title"));
        when(sensitiveWordService.checkText("New body")).thenReturn(PostAuditResult.pass("New body"));
        when(reviewPolicy.shouldReview(any(VibePost.class))).thenReturn(false);

        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("New title");
        request.setContent("New body");

        editService.updatePost(7L, request, 2L);

        verify(agentEvents, never()).publishReview(any(), any());
        verify(agentEvents).publishSafety(eq(post), eq(2L));
    }

    @Test
    @DisplayName("A review-eligible creation pre-writes REVIEWING and dispatches both events")
    void eligibleCreationPreWritesReviewing() {
        when(sensitiveWordService.checkText("Fresh title")).thenReturn(PostAuditResult.pass("Fresh title"));
        when(sensitiveWordService.checkText("```java\nint x = 1;\n```"))
                .thenReturn(PostAuditResult.pass("```java\nint x = 1;\n```"));

        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Fresh title");
        request.setContent("```java\nint x = 1;\n```");
        request.setCategoryId(2);

        creationService.createPost(request, 2L);

        verify(agentEvents).publishReview(any(VibePost.class), eq(2L));
        verify(agentEvents).publishSafety(any(VibePost.class), eq(2L));
    }

    @Test
    @DisplayName("Safety stays independent when review is switched off")
    void reviewFlagDoesNotGateSafety() {
        setAiFlags(false, true);
        when(sensitiveWordService.checkText("New title")).thenReturn(PostAuditResult.pass("New title"));
        when(sensitiveWordService.checkText("New body")).thenReturn(PostAuditResult.pass("New body"));

        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("New title");
        request.setContent("New body");

        editService.updatePost(7L, request, 2L);

        verify(agentEvents, never()).publishReview(any(), any());
        verify(agentEvents).publishSafety(eq(post), eq(2L));
    }
}
