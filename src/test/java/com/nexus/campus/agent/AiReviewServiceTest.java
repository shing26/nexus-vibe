package com.nexus.campus.agent;

import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.repository.AiReviewLogRepository;
import com.nexus.campus.repository.VibeCommentRepository;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.service.SysMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the verdict recorder. Since the reviewer seam (ADR-0012) was
 * implemented, these tests stub a {@link Reviewer} and assert only on what
 * core does with a verdict: the validity gate, the log row, the comment, the
 * score writeback, and the failure notifications. The LLM pipeline itself is
 * covered by the reviewer tests.
 */
@ExtendWith(MockitoExtension.class)
class AiReviewServiceTest {

    @Mock
    private VibePostRepository posts;
    @Mock
    private AiReviewLogRepository reviewLogs;
    @Mock
    private VibeCommentRepository comments;
    @Mock
    private SysMessageService sysMessageService;

    @InjectMocks
    private AiReviewService aiReviewService;

    private VibePost post;

    @BeforeEach
    void setUp() {
        post = new VibePost();
        post.setId(42L);
        post.setTitle("Calculator");
        post.setPostType("post");
        post.setStatus(1);
        post.setContent("```java\nint x = 1;\n```");
    }

    private Reviewer.Verdict validVerdict() {
        return new Reviewer.Verdict(8, "low",
                "Clean structure and readable naming throughout the implementation",
                "No SQL injection or unsafe casts detected",
                "Extract the division logic into a helper and add unit tests",
                true, "**Overall Score**: 8/10", "{\"score\":8}");
    }

    private Reviewer reviewerReturning(Reviewer.Verdict verdict) {
        return new StubReviewer(verdict);
    }

    /** Minimal concrete reviewer whose claims() always matches. */
    private static final class StubReviewer implements Reviewer {
        private final Verdict verdict;

        StubReviewer(Verdict verdict) {
            this.verdict = verdict;
        }

        @Override
        public String identity() {
            return "stub-review-agent";
        }

        @Override
        public boolean claims(VibePost post) {
            return true;
        }

        @Override
        public Verdict review(VibePost post) {
            return verdict;
        }
    }

    @Test
    @DisplayName("A valid verdict is logged, commented and written back with its score")
    void validVerdictShouldBeRecordedFully() {
        aiReviewService.reviewPost(post, reviewerReturning(validVerdict()), 42L, false);

        ArgumentCaptor<AiReviewLog> logCaptor =
                ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(logCaptor.capture());
        assertEquals(42L, logCaptor.getValue().getPostId());
        assertEquals("stub-review-agent", logCaptor.getValue().getReviewer());
        assertEquals("low", logCaptor.getValue().getSeverity());
        assertEquals(1, logCaptor.getValue().getIsApproved());

        // AI comment posted on the post, headed by the shared marker
        ArgumentCaptor<VibeComment> commentCaptor = ArgumentCaptor.forClass(VibeComment.class);
        verify(comments).insert(commentCaptor.capture());
        VibeComment comment = commentCaptor.getValue();
        assertEquals(42L, comment.getPostId());
        assertEquals(999L, comment.getUserId());
        assertTrue(comment.getContent().startsWith("## AI Code Review"));
        assertTrue(comment.getContent().contains("8/10"));
        verify(posts).recalculateCommentCount(42L);

        // score written back to the post
        ArgumentCaptor<VibePost> postCaptor = ArgumentCaptor.forClass(VibePost.class);
        verify(posts).update(postCaptor.capture());
        assertEquals(42L, postCaptor.getValue().getId());
        assertEquals(8, postCaptor.getValue().getAiReviewScore());
        assertEquals(1, postCaptor.getValue().getAiReviewed());

        // a successful review does not ping the author
        verify(sysMessageService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A null verdict marks the post FAILED and logs 'unavailable'")
    void nullVerdictShouldMarkFailedAndNotify() {
        aiReviewService.reviewPost(post, reviewerReturning(null), 99L, false);

        ArgumentCaptor<AiReviewLog> logCaptor =
                ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(logCaptor.capture());
        assertEquals("unavailable", logCaptor.getValue().getSeverity());
        verify(comments, never()).insert(any(VibeComment.class));
        verify(sysMessageService).sendMessage(any(), eq(99L), contains("自动重试"), eq(3));
        ArgumentCaptor<VibePost> postCaptor = ArgumentCaptor.forClass(VibePost.class);
        verify(posts).update(postCaptor.capture());
        assertEquals(3, postCaptor.getValue().getAiReviewed());
        assertNull(postCaptor.getValue().getAiReviewScore());
    }

    @Test
    @DisplayName("A verdict failing the validity gate logs 'unknown' and marks FAILED")
    void invalidVerdictShouldDegrate() {
        Reviewer.Verdict placeholder = new Reviewer.Verdict(0, "low",
                "EVALUATE", "LOW", "",
                false, "placeholder body", "{\"score\":0}");
        aiReviewService.reviewPost(post, reviewerReturning(placeholder), 7L, false);

        ArgumentCaptor<AiReviewLog> logCaptor =
                ArgumentCaptor.forClass(AiReviewLog.class);
        verify(reviewLogs).insert(logCaptor.capture());
        assertEquals("unknown", logCaptor.getValue().getSeverity());
        assertNotNull(logCaptor.getValue().getResultJson());
        verify(comments, never()).insert(any(VibeComment.class));
        verify(sysMessageService).sendMessage(any(), eq(7L), contains("自动重试"), eq(3));
        ArgumentCaptor<VibePost> postCaptor = ArgumentCaptor.forClass(VibePost.class);
        verify(posts).update(postCaptor.capture());
        assertEquals(3, postCaptor.getValue().getAiReviewed());
    }

    @Test
    @DisplayName("Reconciliation retries fail silently: no author notification")
    void retriedReviewShouldNotNotify() {
        aiReviewService.reviewPost(post, reviewerReturning(null), 99L, true);

        verify(sysMessageService, never()).sendMessage(any(), any(), any(), any());
        verify(posts).update(any(VibePost.class));
    }
}
