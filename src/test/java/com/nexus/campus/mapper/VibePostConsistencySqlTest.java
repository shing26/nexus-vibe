package com.nexus.campus.mapper;

import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class VibePostConsistencySqlTest {

    @Autowired
    private VibePostMapper vibePostMapper;
    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;
    @Autowired
    private VibeCommentMapper vibeCommentMapper;

    private void post(long id, int status) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setUserId(2L);
        post.setCategoryId(2);
        post.setTitle("status guard " + id);
        post.setContent("body");
        post.setStatus(status);
        vibePostMapper.insert(post);
    }

    private void safetyLog(long postId, String severity) {
        AiReviewLog log = new AiReviewLog();
        log.setPostId(postId);
        log.setReviewer("safety-check-agent");
        log.setResultJson("test");
        log.setSeverity(severity);
        log.setIsApproved(0);
        aiReviewLogMapper.insert(log);
    }

    private void comment(long id, long postId, int status) {
        VibeComment comment = new VibeComment();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setUserId(2L);
        comment.setContent("comment " + id);
        comment.setStatus(status);
        vibeCommentMapper.insert(comment);
    }

    @Test
    @DisplayName("A pending-llm hold can be restored by a safe recheck")
    void pendingLlmHoldCanBeRestored() {
        post(9200L, 2);
        safetyLog(9200L, "pending-llm");

        assertEquals(1, vibePostMapper.restoreActiveAfterSafetyRecovery(9200L));
        assertEquals(1, vibePostMapper.selectById(9200L).getStatus());
    }

    @Test
    @DisplayName("A human rejection is never overwritten by a safe recheck")
    void humanRejectionIsNotRestored() {
        post(9201L, 3);
        safetyLog(9201L, "pending-llm");

        assertEquals(0, vibePostMapper.restoreActiveAfterSafetyRecovery(9201L));
        assertEquals(3, vibePostMapper.selectById(9201L).getStatus());
    }

    @Test
    @DisplayName("A later safety verdict supersedes an older pending-llm marker")
    void promptInjectionHoldIsNotRestored() {
        post(9202L, 2);
        safetyLog(9202L, "pending-llm");
        safetyLog(9202L, "critical");

        assertEquals(0, vibePostMapper.restoreActiveAfterSafetyRecovery(9202L));
        assertEquals(2, vibePostMapper.selectById(9202L).getStatus());
    }

    @Test
    @DisplayName("Comment count is recomputed from visible comment rows")
    void commentCountIsRecomputedFromVisibleRows() {
        post(9203L, 1);
        VibePost stored = vibePostMapper.selectById(9203L);
        stored.setCommentCount(99);
        vibePostMapper.updateById(stored);
        comment(92030L, 9203L, 1);
        comment(92031L, 9203L, 1);
        comment(92032L, 9203L, 2);

        assertEquals(1, vibePostMapper.recalculateCommentCount(9203L));
        assertEquals(2, vibePostMapper.selectById(9203L).getCommentCount());
    }
}
