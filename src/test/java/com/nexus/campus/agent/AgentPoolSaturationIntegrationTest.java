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
import static org.junit.jupiter.api.Assertions.fail;
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
     * How long the polls wait for the fail-closed write. Nothing on that path waits behind
     * {@code HOLD_SECONDS}: the hold is released as soon as the dispatch has been attempted
     * (see {@link #releaseHoldAfterDispatch()}), so both arrival paths start from the commit.
     * The pool either refuses the dispatch, and the publisher's recovery transaction writes
     * inline in {@code afterCommit}, or it takes the submission — CI runs have shown this can
     * happen even immediately after the precondition below proved rejection — and the queued
     * listener starts as soon as the released tasks free their slots. The mocked
     * {@link LlmClient} keeps that second path short too, so what remains is scheduling
     * latency on a shared CI runner. The deadline covers that with room to spare; a timeout
     * here is a failure with diagnostics, not a quiet return (see {@link #awaitPendingReview}).
     */
    private static final long POLL_DEADLINE_SECONDS = 20;

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
        // Held in a local as well as the field: the tasks must await the gate this test opened,
        // never one that happens to be sitting in the field when the previous test's teardown
        // runs. The field alone is how a leftover task ends up waiting on the wrong latch.
        CountDownLatch gate = new CountDownLatch(1);
        release = gate;
        ThreadPoolExecutor pool = pool();
        // Submit until the pool refuses: it grows to core, then fills its queue, then grows to
        // max, and only then rejects. Filling it exactly means stopping at the first refusal --
        // but a single refusal is not proof that the pool is still full a moment later, so the
        // probe below is part of the loop: if it is accepted, a slot freed up between the two
        // calls, the fill is repeated, and the test does not assert on a pool it never held.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(HOLD_SECONDS);
        while (System.nanoTime() < deadline) {
            boolean refused = false;
            for (int i = 0; i < 1000 && !refused; i++) {
                try {
                    hold(pool, gate);
                } catch (RejectedExecutionException full) {
                    refused = true;
                }
            }
            if (!refused) {
                // Everything we submitted was taken, so the pool is not full by construction.
                sleepPollingInterval();
                continue;
            }
            if (refuses(pool)) {
                log.info("agent pool saturated for the test: active={}, poolSize={}, queued={}",
                        pool.getActiveCount(), pool.getPoolSize(), pool.getQueue().size());
                return;
            }
        }
        fail("the agent pool could not be held saturated within " + HOLD_SECONDS + "s: corePoolSize="
                + pool.getCorePoolSize() + ", maxPoolSize=" + pool.getMaximumPoolSize()
                + ", poolSize=" + pool.getPoolSize() + ", active=" + pool.getActiveCount()
                + ", queued=" + pool.getQueue().size()
                + ", queue=" + pool.getQueue().getClass().getSimpleName()
                + ", remainingCapacity=" + pool.getQueue().remainingCapacity()
                + ", gate=" + gate.getCount());
    }

    /** True when the pool refuses new work right now; a one-off probe, not a sentinel task. */
    private boolean refuses(ThreadPoolExecutor pool) {
        try {
            pool.execute(() -> { });
            return false;
        } catch (RejectedExecutionException refused) {
            return true;
        }
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
        releaseHoldAfterDispatch();

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
        releaseHoldAfterDispatch();

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
     * Releases the hold tasks as soon as the dispatch has been attempted.
     *
     * <p>The saturation only has to exist at dispatch time. The publisher registers an
     * {@code afterCommit} synchronization, and {@code TransactionTemplate.executeWithoutResult}
     * returns only after that callback has run, so the accept-or-reject decision is already made
     * when this is called. Holding the pool any longer only delays the queued listener behind
     * {@code HOLD_SECONDS} for no benefit — which is what made this test race its own deadline on
     * CI.</p>
     */
    private void releaseHoldAfterDispatch() {
        release.countDown();
    }

    /**
     * Reads the post status once the safety check has had the chance to fail closed.
     *
     * <p>The fail-closed write is not guaranteed to land before the commit returns, so this polls.
     * Two arrival paths reach the same write: the saturated pool refuses the dispatch and the
     * publisher's recovery transaction writes the status inline in {@code afterCommit}; or the pool
     * takes the submission and the listener runs once the released hold tasks free their slots.
     * Either way it lands in milliseconds to seconds, never behind {@code HOLD_SECONDS} — see
     * {@link #releaseHoldAfterDispatch()} and {@link #POLL_DEADLINE_SECONDS}.</p>
     *
     * <p>Timing out fails rather than returns. Returning the stored value would make a scheduling
     * timeout indistinguishable from the fail-open outcome this test exists to catch: both would
     * report {@code expected: <2> but was: <1>}, and the message would say nothing about which one
     * happened.</p>
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
        VibePost stored = vibePostMapper.selectById(postId);
        return fail("the fail-closed write did not land within " + POLL_DEADLINE_SECONDS
                + "s: status=" + stored.getStatus() + " (expected " + PostStatus.PENDING_REVIEW.getCode()
                + "), pool active=" + pool().getActiveCount() + ", queued=" + pool().getQueue().size()
                + "; a timeout here means the write never arrived, not that it arrived late");
    }

    /**
     * The same bounded wait for the review path: the listener marks the post FAILED from its own
     * thread on either arrival path, and a timeout fails with diagnostics for the reason
     * {@link #awaitPendingReview} gives.
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
        VibePost stored = vibePostMapper.selectById(postId);
        return fail("the review was not marked FAILED within " + POLL_DEADLINE_SECONDS
                + "s: aiReviewed=" + stored.getAiReviewed() + " (expected " + AiReviewStatus.FAILED.getCode()
                + "), pool active=" + pool().getActiveCount() + ", queued=" + pool().getQueue().size()
                + "; a timeout here means the write never arrived, not that it arrived late");
    }

    private void sleepPollingInterval() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void hold(ThreadPoolExecutor pool, CountDownLatch gate) {
        // The gate is the caller's local, not the field: the field is replaced by the next test,
        // and a task left over from this one would then block on the next test's latch and
        // corrupt its pool accounting. That is what made this class flaky on CI.
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
