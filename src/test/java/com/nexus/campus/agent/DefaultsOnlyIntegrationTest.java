package com.nexus.campus.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.enums.PostStatus;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.impl.PostAgentEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The restored half of the AI-4 pair. {@link BothSeamsReplacedIntegrationTest}
 * swaps both ADR-0012 seams and asserts a plain-prose post gets the canned
 * review; this class runs the same post shape against the untouched context
 * and asserts the default rule skips it and nothing is recorded.
 *
 * <p>The pair is the evidence, per the ticket: a replacement that fires could
 * be the default policy matching anyway, and a skipped post could be a
 * replacement failing silently. Both halves must hold for the seams to count
 * as proven. Spring TestContext caches one context per unique configuration,
 * so the two halves run in two classes — this one carries no
 * {@code TestConfiguration}, and its context is the default wiring exactly
 * as production resolves it.</p>
 */
@SpringBootTest
@Sql({"/data.sql", "/test-users.sql"})
class DefaultsOnlyIntegrationTest {

    private static final long AGENT_USER_ID = 999L;
    private static final String PROSE_TITLE = "prose post 9401";
    private static final String PROSE_CONTENT = "A plain prose post with no fenced code block and a post type of post, "
            + "which the default policy's code half and prompt half both decline to review.";

    @Autowired
    private PostAgentEventPublisher publisher;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Defaults restored: the same prose shape the replacement reviewed is skipped, with no row")
    void defaultsSkipTheProseShapeTheReplacementReviewed() {
        long postId = 9402L;
        insertProsePost(postId);
        VibePost prose = vibePostMapper.selectById(postId);

        // Same dispatch as the replaced half: a real transaction that really
        // commits, so the listener runs against the committed post.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishReview(prose, prose.getUserId());
        });

        // Give the async dispatch a fair window to run — or to demonstrate it
        // never will — before asserting on the quiet outcome. The wait itself
        // is not the assertion; the unchanged state is.
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        VibePost stored = vibePostMapper.selectById(postId);
        assertEquals(AiReviewStatus.NOT_REVIEWED.getCode(), stored.getAiReviewed(),
                "the default policy must skip the prose shape; a review here would mean the "
                        + "replacement test proved nothing about the swap");
        assertEquals(0, stored.getAiReviewScore(), "no score may be written for a skipped post");

        List<AiReviewLog> rows = aiReviewLogMapper.selectList(
                new LambdaQueryWrapper<AiReviewLog>().eq(AiReviewLog::getPostId, postId));
        assertTrue(rows.isEmpty(), "no review log row may exist for a post the default rule skips");
    }

    private void insertProsePost(long id) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setTitle(PROSE_TITLE);
        post.setContent(PROSE_CONTENT);
        post.setUserId(2L);
        post.setCategoryId(2);
        post.setStatus(PostStatus.ACTIVE.getCode());
        post.setAiReviewed(AiReviewStatus.NOT_REVIEWED.getCode());
        vibePostMapper.insert(post);
    }
}
