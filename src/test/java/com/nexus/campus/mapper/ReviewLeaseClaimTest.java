package com.nexus.campus.mapper;

import com.nexus.campus.entity.VibePost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lease-claim semantics against H2 (ADR-0005): mutual exclusion, expiry and
 * the attempt budget. The claim is a single conditional UPDATE, so H2 proves
 * the SQL contract; cross-instance races reduce to the same statement.
 * Expiry is judged by the DB clock (NOW()), never by the caller's timestamp.
 */
@SpringBootTest
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class ReviewLeaseClaimTest {

    @Autowired
    private VibePostMapper vibePostMapper;

    private VibePost post(long id, int aiReviewed) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setTitle("lease test post " + id);
        post.setContent("body");
        post.setUserId(2L);
        post.setCategoryId(2);
        post.setStatus(1);
        post.setAiReviewed(aiReviewed);
        vibePostMapper.insert(post);
        return post;
    }

    /** Forces lease expiry as the DB sees it (a past lock_until row value). */
    private void expireLease(long id, long secondsAgo) {
        VibePost stored = vibePostMapper.selectById(id);
        stored.setReviewLockUntil(LocalDateTime.now().minusSeconds(secondsAgo));
        vibePostMapper.updateById(stored);
    }

    @Test
    @DisplayName("First claim wins and increments attempts")
    void firstClaimWins() {
        post(9100L, 0);

        int claimed = vibePostMapper.tryClaimReview(9100L, LocalDateTime.now().plusSeconds(30), "nodeA", 5);
        assertEquals(1, claimed);
        assertEquals(1, vibePostMapper.selectReviewAttempts(9100L));
    }

    @Test
    @DisplayName("Second claim while lease is live is refused (mutual exclusion)")
    void secondClaimRefusedWhileLive() {
        post(9101L, 0);

        assertEquals(1, vibePostMapper.tryClaimReview(9101L, LocalDateTime.now().plusSeconds(30), "nodeA", 5));
        // concurrent instance / re-published event inside the lease window
        assertEquals(0, vibePostMapper.tryClaimReview(9101L, LocalDateTime.now().plusSeconds(30), "nodeB", 5));
        assertEquals(1, vibePostMapper.selectReviewAttempts(9101L));
    }

    @Test
    @DisplayName("Expired lease (per the DB clock) can be re-claimed by another owner")
    void expiredLeaseReclaimable() {
        post(9102L, 0);

        assertEquals(1, vibePostMapper.tryClaimReview(9102L, LocalDateTime.now().plusSeconds(30), "nodeA", 5));
        expireLease(9102L, 60);
        assertEquals(1, vibePostMapper.tryClaimReview(9102L, LocalDateTime.now().plusSeconds(30), "nodeB", 5));
        assertEquals(2, vibePostMapper.selectReviewAttempts(9102L));
    }

    @Test
    @DisplayName("Attempt budget exhausted: claim refused at max attempts")
    void budgetExhausted() {
        post(9103L, 0);

        for (int i = 1; i <= 5; i++) {
            assertEquals(1, vibePostMapper.tryClaimReview(9103L, LocalDateTime.now().plusSeconds(30), "node", 5),
                    "claim " + i + " should succeed");
            expireLease(9103L, 60);
        }
        assertEquals(5, vibePostMapper.selectReviewAttempts(9103L));
        // 6th attempt refused even with an expired lease
        assertEquals(0, vibePostMapper.tryClaimReview(9103L, LocalDateTime.now().plusSeconds(30), "node", 5));
    }
}
