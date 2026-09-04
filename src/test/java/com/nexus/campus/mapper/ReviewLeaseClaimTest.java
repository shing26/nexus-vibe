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

    @Test
    @DisplayName("First claim wins and increments attempts")
    void firstClaimWins() {
        post(9100L, 0);
        LocalDateTime now = LocalDateTime.now();

        int claimed = vibePostMapper.tryClaimReview(9100L, now.plusSeconds(30), "nodeA", now, 5);
        assertEquals(1, claimed);
        assertEquals(1, vibePostMapper.selectReviewAttempts(9100L));
    }

    @Test
    @DisplayName("Second claim while lease is live is refused (mutual exclusion)")
    void secondClaimRefusedWhileLive() {
        post(9101L, 0);
        LocalDateTime now = LocalDateTime.now();

        assertEquals(1, vibePostMapper.tryClaimReview(9101L, now.plusSeconds(30), "nodeA", now, 5));
        // concurrent instance / re-published event inside the lease window
        assertEquals(0, vibePostMapper.tryClaimReview(9101L, now.plusSeconds(30), "nodeB", now, 5));
        assertEquals(1, vibePostMapper.selectReviewAttempts(9101L));
    }

    @Test
    @DisplayName("Expired lease can be re-claimed by another owner")
    void expiredLeaseReclaimable() {
        post(9102L, 0);
        LocalDateTime now = LocalDateTime.now();

        assertEquals(1, vibePostMapper.tryClaimReview(9102L, now.plusSeconds(30), "nodeA", now, 5));
        // advance past the lease window
        LocalDateTime later = now.plusSeconds(31);
        assertEquals(1, vibePostMapper.tryClaimReview(9102L, later.plusSeconds(30), "nodeB", later, 5));
        assertEquals(2, vibePostMapper.selectReviewAttempts(9102L));
    }

    @Test
    @DisplayName("Attempt budget exhausted: claim refused at max attempts")
    void budgetExhausted() {
        post(9103L, 0);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= 5; i++) {
            LocalDateTime t = now.plusSeconds(31L * i);
            assertEquals(1, vibePostMapper.tryClaimReview(9103L, t.plusSeconds(30), "node", t, 5),
                    "claim " + i + " should succeed");
        }
        assertEquals(5, vibePostMapper.selectReviewAttempts(9103L));
        // 6th attempt refused even with an expired lease
        LocalDateTime t6 = now.plusSeconds(31L * 6);
        assertEquals(0, vibePostMapper.tryClaimReview(9103L, t6.plusSeconds(30), "node", t6, 5));
    }
}
