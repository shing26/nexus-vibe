package com.nexus.campus.service;

import com.nexus.campus.dto.CommentCreateRequest;
import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.CommentSentiment;
import com.nexus.campus.mapper.SysMessageMapper;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.metrics.ProductMetrics;
import com.nexus.campus.service.impl.CommentAgentEventPublisher;
import com.nexus.campus.service.impl.VibeCommentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sentiment rule's contract with the write path (A7-4): classification is
 * cosmetic, so it must never be the reason a comment is lost. The classifier
 * is replaced through the one seam the service exposes for it.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceSentimentTest {

    private static final Long POST_ID = 42L;
    private static final Long USER_ID = 100L;
    private static final Long AUTHOR_ID = 200L;

    @Mock
    private VibeCommentMapper vibeCommentMapper;

    @Mock
    private VibePostMapper vibePostMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysMessageMapper sysMessageMapper;

    @Mock
    private SensitiveWordService sensitiveWordService;

    @Mock
    private ProductMetrics productMetrics;

    @Mock
    private CommentAgentEventPublisher commentAgentEventPublisher;

    @InjectMocks
    private VibeCommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        VibePost post = new VibePost();
        post.setId(POST_ID);
        post.setUserId(AUTHOR_ID);
        post.setTitle("Test Post");

        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setCorePower(10);

        when(vibePostMapper.selectById(POST_ID)).thenReturn(post);
        when(sysUserMapper.selectById(USER_ID)).thenReturn(user);
    }

    @Test
    @DisplayName("a throwing classifier still saves the comment, classified as other")
    void throwingClassifierNeverFailsTheWrite() {
        ReflectionTestUtils.setField(commentService, "sentimentClassifier",
                (VibeCommentServiceImpl.SentimentClassifier) text -> {
                    throw new IllegalStateException("rule exploded");
                });

        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(POST_ID);
        request.setContent("Great post, thanks for sharing.");
        when(sensitiveWordService.checkText(request.getContent()))
                .thenReturn(PostAuditResult.pass(request.getContent()));

        VibeComment result = commentService.createComment(request, USER_ID);

        assertNotNull(result);
        ArgumentCaptor<VibeComment> captor = ArgumentCaptor.forClass(VibeComment.class);
        verify(vibeCommentMapper).insert(captor.capture());
        assertEquals(CommentSentiment.OTHER.getCode(), captor.getValue().getSentiment());
        // Status is untouched by the failure: the visible-first ordering is unchanged.
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("the production classifier labels the stored text, not the raw request")
    void classifiesTheStoredText() {
        // The DFA filter rewrites the content before it is stored; the label
        // must describe the row a reader will see. The raw text reads as
        // praise, the filtered text as a complaint.
        when(sensitiveWordService.checkText("Nice post."))
                .thenReturn(PostAuditResult.pass("Nice post, but the bug crashed it."));

        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(POST_ID);
        request.setContent("Nice post.");

        VibeComment result = commentService.createComment(request, USER_ID);

        assertEquals(CommentSentiment.COMPLAINT.getCode(), result.getSentiment());
        verify(vibeCommentMapper).insert(any(VibeComment.class));
    }
}
