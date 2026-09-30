package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.enums.PostStatus;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.impl.PostAgentEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * The guarantee this file exists to protect: a saturated agent-llm pool must never leave a post
 * publicly visible without a safety verdict.
 *
 * <p>The regression it pins down came from moving the agent listeners to
 * {@code @TransactionalEventListener(AFTER_COMMIT)}. That phase is correct — a listener running
 * mid-transaction reads a post nothing else can see — but it also moves the executor submission to
 * after the commit. {@code PostAgentEventPublisher} caught the resulting rejection around
 * {@code publishEvent}; once the phase changed, {@code publishEvent} returned normally and the
 * rejection surfaced inside a transaction-synchronization callback that Spring swallows. The
 * catch became unreachable, so the safety {@code pending-llm} marker was never written — and since
 * {@code selectPostsPendingSafetyRecheck} keys on that marker, reconciliation could not find the
 * post either. The net effect was a public post moderation never ran on, permanently: the exact
 * fail-open outcome ADR-0004 exists to prevent.</p>
 *
 * <p>Both halves run against a genuinely saturated pool and a real commit, because neither a
 * mocked publisher nor the reflection assertion that shipped with the listener change can tell
 * "the catch ran" apart from "the catch was unreachable".</p>
 */
@SpringBootTest
@Sql({"/data.sql", "/test-users.sql"})
class AgentPoolSaturationIntegrationTest {

    /** Bounds the blocking tasks, so a leaked thread cannot hang the suite. */
    private static final long HOLD_SECONDS = 30;

    @Autowired
    private PostAgentEventPublisher publisher;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("agentLlmExecutor")
    private ThreadPoolTaskExecutor agentLlmExecutor;

    /**
     * Forced healthy so the publish-time health gate lets the event through to the pool. Without
     * this the test would still fail closed, but for the wrong reason, and would keep passing even
     * if the saturation handling were deleted.
     */
    @MockBean
    private LlmHealthCache llmHealthCache;

    private CountDownLatch release;

    @BeforeEach
    void occupyAgentPool() {
        when(llmHealthCache.isHealthy()).thenReturn(true);
        release = new CountDownLatch(1);
        ThreadPoolExecutor pool = pool();
        // Submit until the pool refuses: it grows to core, then fills its queue, then grows to
        // max, and only then rejects. Stopping at the first rejection fills it exactly.
        for (int i = 0; i < 1000; i++) {
            try {
                hold(pool);
            } catch (RejectedExecutionException saturated) {
                break;
            }
        }
        // Precondition for both tests: this pool really does refuse work.
        assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> { }),
                "test precondition: the agent pool must be saturated");
    }

    @AfterEach
    void drainAgentPool() {
        release.countDown();
    }

    @Test
    @DisplayName("Saturated pool: the safety check fails closed to the audit queue instead of staying public")
    void saturatedPoolFailsSafetyCheckClosed() {
        long postId = 9301L;
        insertActivePost(postId);

        // A real transaction that really commits, because the rejection only happens after commit.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            VibePost post = vibePostMapper.selectById(postId);
            publisher.publishSafety(post, post.getUserId());
            assertEquals(PostStatus.ACTIVE.getCode(), post.getStatus(),
                    "precondition: the post is still ACTIVE inside the transaction");
        });

        VibePost stored = vibePostMapper.selectById(postId);
        assertEquals(PostStatus.PENDING_REVIEW.getCode(), stored.getStatus(),
                "a rejected safety check must fail closed; a publicly visible post here is a moderation bypass");

        AiReviewLog marker = aiReviewLogMapper.selectLatestSafetyLogByPostId(postId);
        assertNotNull(marker,
                "the pending-llm marker is the only handle reconciliation has on this post");
        assertEquals("pending-llm", marker.getSeverity());
    }

    @Test
    @DisplayName("Saturated pool: the review is marked FAILED so reconciliation re-runs it")
    void saturatedPoolMarksReviewFailed() {
        long postId = 9302L;
        insertActivePost(postId);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            VibePost post = vibePostMapper.selectById(postId);
            post.setAiReviewed(AiReviewStatus.REVIEWING.getCode());
            vibePostMapper.updateById(post);
            publisher.publishReview(post, post.getUserId());
        });

        assertEquals(AiReviewStatus.FAILED.getCode(), vibePostMapper.selectById(postId).getAiReviewed(),
                "a rejected review must not sit in REVIEWING with nothing in flight to advance it");
    }

    private void insertActivePost(long id) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setTitle("saturation post " + id);
        post.setContent("```java\nint x = 1;\n```");
        post.setUserId(2L);
        post.setCategoryId(2);
        post.setStatus(PostStatus.ACTIVE.getCode());
        post.setAiReviewed(AiReviewStatus.NOT_REVIEWED.getCode());
        vibePostMapper.insert(post);
    }

    private void hold(ThreadPoolExecutor pool) {
        pool.execute(() -> {
            try {
                release.await(HOLD_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private ThreadPoolExecutor pool() {
        return agentLlmExecutor.getThreadPoolExecutor();
    }
}
