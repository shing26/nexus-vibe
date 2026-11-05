package com.nexus.campus.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.enums.PostStatus;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.impl.PostAgentEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * AI-4: prove the two ADR-0012 seams are not decoration. The saturation
 * tests exercise the default wiring; the listener unit test exercises
 * routing against mocks. Neither answers the question this class answers:
 * does replacing <em>both</em> defaults retarget the real pipeline at a
 * different subject, end to end — the event dispatched, the lease claimed,
 * the replacement's verdict recorded, the row carrying the replacement's
 * identity — for a post the default rule would have skipped?
 *
 * <p>The pair is the evidence. This class holds the replaced half: both
 * seams swapped in one {@code TestConfiguration}, a plain-prose post (which
 * the default policy's code half and prompt half both decline) reviewed by
 * the canned reviewer. {@link DefaultsOnlyIntegrationTest} holds the
 * restored half: the same post shape against the untouched context, skipped
 * with nothing recorded. Either half alone proves nothing — a replacement
 * that fires could be the default policy matching anyway, and a skipped
 * post could be the replacement failing silently — so the two classes
 * assert the pair, in the two distinct Spring contexts their configurations
 * produce.</p>
 *
 * <p>The canned verdict must pass {@code ReviewValidity} — severity from the
 * known set, quality and suggestions past the placeholder pattern and the
 * length floor — because validity lives in core on purpose: a replacement
 * cannot write a verdict core would refuse.</p>
 */
@SpringBootTest
@Sql({"/data.sql", "/test-users.sql"})
class BothSeamsReplacedIntegrationTest {

    private static final long POLL_DEADLINE_SECONDS = 20;
    private static final long AGENT_USER_ID = 999L;
    private static final String REVIEW_COMMENT_MARKER = "## AI Code Review";

    @Autowired
    private PostAgentEventPublisher publisher;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

    @Autowired
    private VibeCommentMapper commentMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ThreadPoolTaskExecutor agentLlmExecutor;

    /**
     * Both seams replaced at once, in one configuration — the point of AI-4.
     * The policy reviews everything. The reviewer replacement is positional
     * rather than by-type: the listener routes to the first reviewer that
     * claims, and the canned reviewer claims before the defaults are
     * consulted, so no {@code @Primary} contest on the reviewer type is
     * needed. The policy is by-type injected and does need
     * {@code @Primary} to win against the default bean.
     */
    @TestConfiguration
    static class BothSeamsReplaced {

        @Bean
        @Primary
        ReviewPolicy retargetedReviewPolicy() {
            return post -> true;
        }

        @Bean
        Reviewer cannedReviewer() {
            return new CannedReviewer();
        }
    }

    /** Visible for the sibling class's javadoc; the verdict body names AI-4. */
    static final class CannedReviewer implements Reviewer {
        @Override
        public String identity() {
            return "canned-review-agent";
        }

        @Override
        public boolean claims(VibePost post) {
            return true;
        }

        @Override
        public Verdict review(VibePost post) {
            return new Verdict(5, "medium",
                    "The canned replacement reviewed this post; its quality notes come from the test, not a model.",
                    "No security analysis happened; this row only proves the replacement ran.",
                    "Nothing to suggest; the body exists to pass the validity gate.",
                    true,
                    "## AI Code Review\n\nRecorded by the canned replacement (AI-4).",
                    "{\"score\":5}");
        }
    }

    @AfterEach
    void drainAgentPool() {
        ThreadPoolExecutor pool = agentLlmExecutor.getThreadPoolExecutor();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_DEADLINE_SECONDS);
        while (System.nanoTime() < deadline) {
            if (pool.getActiveCount() == 0 && pool.getQueue().isEmpty()) {
                return;
            }
            sleepPollingInterval();
        }
        fail("agent pool did not drain; the next test cannot trust it");
    }

    @Test
    @DisplayName("Both seams replaced: a post the default skips is reviewed, and the row carries the replacement's identity")
    void bothSeamsReplacedReviewsSkippedPost() {
        long postId = 9401L;
        // Plain prose, no code fence, not a prompt: the default policy's two
        // halves both decline this shape. If the canned review lands, it is
        // the replacement that did it — the default could not have fired.
        insertProsePost(postId);
        VibePost prose = vibePostMapper.selectById(postId);

        publishReview(prose);

        int aiReviewed = awaitReviewed(postId);
        assertEquals(AiReviewStatus.REVIEWED.getCode(), aiReviewed,
                "the replacement must carry the post past the default policy's skip");

        VibePost stored = vibePostMapper.selectById(postId);
        assertEquals(5, stored.getAiReviewScore(),
                "the recorded score is the canned verdict's, proving the replacement produced it");

        // The recorded row carries the replacement's identity — the score is
        // not enough, an identity-less write would be indistinguishable from
        // the default reviewer's.
        List<AiReviewLog> rows = aiReviewLogMapper.selectList(
                new LambdaQueryWrapper<AiReviewLog>().eq(AiReviewLog::getPostId, postId));
        assertEquals(1, rows.size(), "exactly one review log row for the fresh post");
        assertEquals("canned-review-agent", rows.get(0).getReviewer(),
                "the log row must carry the replacement's identity, not the default's");
        assertEquals("medium", rows.get(0).getSeverity());
        assertEquals(1, rows.get(0).getIsApproved());

        // The comment path ran too: one visible AI comment, from the agent
        // account, carrying the canned body.
        List<VibeComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<VibeComment>().eq(VibeComment::getPostId, postId));
        assertEquals(1, comments.size());
        assertEquals(AGENT_USER_ID, comments.get(0).getUserId());
        assertTrue(comments.get(0).getContent().startsWith(REVIEW_COMMENT_MARKER));
        assertTrue(comments.get(0).getContent().contains("Recorded by the canned replacement (AI-4)"));
    }

    private int awaitReviewed(long postId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_DEADLINE_SECONDS);
        while (System.nanoTime() < deadline) {
            VibePost stored = vibePostMapper.selectById(postId);
            if (stored.getAiReviewed() == AiReviewStatus.REVIEWED.getCode()) {
                return stored.getAiReviewed();
            }
            sleepPollingInterval();
        }
        VibePost stored = vibePostMapper.selectById(postId);
        return fail("the canned review did not land within " + POLL_DEADLINE_SECONDS
                + "s: aiReviewed=" + stored.getAiReviewed() + " (expected "
                + AiReviewStatus.REVIEWED.getCode() + "), pool active=" + agentLlmExecutor.getActiveCount()
                + ", queued=" + agentLlmExecutor.getThreadPoolExecutor().getQueue().size()
                + "; a timeout here means the write never arrived, not that it arrived late");
    }

    private void publishReview(VibePost post) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishReview(post, post.getUserId());
        });
    }

    private void insertProsePost(long id) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setTitle("prose post " + id);
        post.setContent("A plain prose post with no fenced code block and a post type of post, "
                + "which the default policy's code half and prompt half both decline to review.");
        post.setUserId(2L);
        post.setCategoryId(2);
        post.setStatus(PostStatus.ACTIVE.getCode());
        post.setAiReviewed(AiReviewStatus.NOT_REVIEWED.getCode());
        vibePostMapper.insert(post);
    }

    private void sleepPollingInterval() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
