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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AgentPoolSaturationIntegrationTest.class);

    /** Bounds the blocking tasks, so a leaked thread cannot hang the suite. */
    private static final long HOLD_SECONDS = 30;

    /**
     * How long the polls wait for the fail-closed write. There are two ways the
     * write can land: the pool refuses the dispatch and the publisher's recovery
     * transaction writes it within milliseconds, or the pool takes the submission —
     * CI runs have shown this can happen even immediately after the precondition
     * below proved rejection — and the queued listener only starts once the hold
     * tasks release at {@code HOLD_SECONDS}. The deadline is the hold plus budget
     * for scheduling the released listener; with {@link #llmClient} mocked the
     * listener does no network work of its own.
     */
    private static final long POLL_DEADLINE_SECONDS = HOLD_SECONDS + 10;

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

    /**
     * Mocked so the queued-listener path is deterministic. When the saturated pool accepts the
     * dispatch, the listener only starts once the hold tasks release, and with a real client its
     * fail-closed write then waits behind the LLM client's retry ladder — a duration this test does
     * not control and CI networking moves around (one run needed over 50s). The subject here is the
     * saturation handling, not the ladder: an unstubbed mock makes every accepted dispatch reach
     * {@code failClosed} as soon as the listener is scheduled, and the rejection path never touches
     * the client at all.
     */
    @MockBean
    private LlmClient llmClient;

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
        // Diagnostic for an open question: two CI runs showed the dispatch's @Async submission
        // accepted right after this precondition proved rejection, so the snapshot records what
        // the pool actually looks like at the moment the test body starts.
        log.info("agent pool saturated for the test: active={}, poolSize={}, queued={}",
                pool.getActiveCount(), pool.getPoolSize(), pool.getQueue().size());
    }

    @AfterEach
    void drainAgentPool() {
        release.countDown();
        awaitPoolEmpty();
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

        int status = awaitPendingReview(postId);
        assertEquals(PostStatus.PENDING_REVIEW.getCode(), status,
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

        int aiReviewed = awaitReviewFailed(postId);
        assertEquals(AiReviewStatus.FAILED.getCode(), aiReviewed,
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

    /**
     * Reads the post status once the safety check has had the chance to fail closed.
     *
     * <p>The fail-closed write is not guaranteed to land before the commit returns. When the
     * saturated pool refuses the event, the publisher's recovery transaction writes the status
     * inline in afterCommit; when the pool takes the submission instead (see
     * {@link #POLL_DEADLINE_SECONDS}), the listener runs asynchronously — queued behind the hold
     * tasks, released at {@code HOLD_SECONDS}. Either way the post ends held in the audit queue;
     * the deadline covers the slower arrival, not just the fast one.</p>
     */
    private int awaitPendingReview(long postId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_DEADLINE_SECONDS);
        while (System.nanoTime() < deadline) {
            VibePost stored = vibePostMapper.selectById(postId);
            if (stored.getStatus() == PostStatus.PENDING_REVIEW.getCode()) {
                return stored.getStatus();
            }
            sleepPollingInterval();
        }
        return vibePostMapper.selectById(postId).getStatus();
    }

    /**
     * Same bounded wait for the review path: the listener also marks a post FAILED from its own
     * thread, whether it ran on a slot freed before the dispatch or on the queued task released
     * with the hold tasks.
     */
    private int awaitReviewFailed(long postId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_DEADLINE_SECONDS);
        while (System.nanoTime() < deadline) {
            VibePost stored = vibePostMapper.selectById(postId);
            if (stored.getAiReviewed() == AiReviewStatus.FAILED.getCode()) {
                return stored.getAiReviewed();
            }
            sleepPollingInterval();
        }
        return vibePostMapper.selectById(postId).getAiReviewed();
    }

    private void sleepPollingInterval() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void hold(ThreadPoolExecutor pool) {
        // Captured, not read from the field: the field is replaced by the next test, and a task
        // left over from this one would then block on the next test's latch and corrupt its
        // pool accounting. That is what made this class flaky on CI.
        CountDownLatch gate = release;
        pool.execute(() -> {
            try {
                gate.await(HOLD_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Waits for every task this test submitted to finish, so the next one refills a pool that is
     * genuinely empty rather than one still draining the previous test's work.
     */
    private void awaitPoolEmpty() {
        ThreadPoolExecutor pool = pool();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(HOLD_SECONDS);
        while (System.nanoTime() < deadline) {
            if (pool.getActiveCount() == 0 && pool.getQueue().isEmpty() ) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("agent pool did not drain; the next test cannot trust it");
    }

    private ThreadPoolExecutor pool() {
        return agentLlmExecutor.getThreadPoolExecutor();
    }
}
