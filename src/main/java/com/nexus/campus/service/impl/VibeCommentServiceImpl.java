package com.nexus.campus.service.impl;

import com.nexus.campus.dto.CommentCreateRequest;
import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.entity.*;
import com.nexus.campus.enums.CommentSentiment;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.mapper.*;
import com.nexus.campus.metrics.ProductMetrics;
import com.nexus.campus.security.AdminGuard;
import com.nexus.campus.service.CommentSentimentClassifier;
import com.nexus.campus.service.SensitiveWordService;
import com.nexus.campus.service.VibeCommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class VibeCommentServiceImpl implements VibeCommentService {

    /**
     * The one seam the sentiment rule sits behind. Inline-initialised rather
     * than injected: the classifier is pure and has no collaborators, so a
     * container bean would only make it look more capable than it is. Tests
     * replace it to prove the write path survives a classifier that throws.
     */
    @FunctionalInterface
    public interface SentimentClassifier {
        int classify(String text);
    }

    @Autowired
    private VibeCommentMapper vibeCommentMapper;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    private ProductMetrics productMetrics;

    @Autowired
    private CommentAgentEventPublisher commentAgentEventPublisher;

    /** Replaced only by tests; the production value is the pure rule function. */
    private SentimentClassifier sentimentClassifier = CommentSentimentClassifier::classify;

    @Override
    @Transactional
    public VibeComment createComment(CommentCreateRequest request, Long userId) {
        VibeComment comment = new VibeComment();
        comment.setPostId(request.getPostId());
        comment.setUserId(userId);
        comment.setParentId(request.getParentId() != null ? request.getParentId() : 0L);
        comment.setTargetId(request.getTargetId() != null ? request.getTargetId() : 0L);
        comment.setContent(request.getContent());

        // DFA check: filter regular sensitive words, queue critical content for audit.
        PostAuditResult audit = sensitiveWordService.checkText(request.getContent());
        comment.setContent(audit.getFilteredContent());
        comment.setStatus(audit.isContainsCritical() ? 2 : 1);
        comment.setSentiment(classifyWithoutFailingTheWrite(comment.getContent()));

        vibeCommentMapper.insert(comment);
        // Counted at the insert, not after the notification and the core-power award:
        // those are side effects of a comment that already exists.
        productMetrics.recordCommentCreated();

        // Update the denormalised count from the rows that are actually
        // visible. Incrementing blindly also counted comments held for audit.
        VibePost post = vibePostMapper.selectById(request.getPostId());
        if (post != null) {
            vibePostMapper.recalculateCommentCount(request.getPostId());

            // Send notification to post author (if not self-comment)
            if (!post.getUserId().equals(userId)) {
                SysMessage msg = new SysMessage();
                msg.setFromUserId(userId);
                msg.setToUserId(post.getUserId());
                msg.setContent("replied to your post: \"" + post.getTitle() + "\"");
                msg.setType(1);
                msg.setIsRead(0);
                sysMessageMapper.insert(msg);
            }
        }

        // Award core power
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            user.setCorePower(user.getCorePower() + 2);
            sysUserMapper.updateById(user);
        }

        // Visible-first safety check (ADR-0017): the comment is already public;
        // the publisher defers the event to after this transaction commits.
        commentAgentEventPublisher.publishSafetyCheck(comment, userId);

        // The mention trigger (ADR-0018) rides the same path: only comments
        // that summon @ai-agent dispatch the reply job.
        commentAgentEventPublisher.publishMentionReply(comment, userId);

        return comment;
    }

    /**
     * Classifies the text that is actually stored — the DFA-filtered content,
     * not the raw request — so the label describes the row a reader will see.
     *
     * <p>Fail-open by contract: sentiment is a cosmetic tab split, so a rule
     * that throws must never cost a comment. Any exception falls back to
     * {@link CommentSentiment#OTHER} and the write proceeds; the failure is
     * logged, not surfaced.
     */
    private int classifyWithoutFailingTheWrite(String storedContent) {
        try {
            return sentimentClassifier.classify(storedContent);
        } catch (RuntimeException e) {
            log.warn("Comment sentiment classification failed; storing as other: {}", e.toString());
            return CommentSentiment.OTHER.getCode();
        }
    }

    @Override
    public List<VibeComment> getCommentsByPostId(Long postId) {
        return vibeCommentMapper.selectCommentsByPostId(postId);
    }

    @Override
    public int countCommentsByPostId(Long postId) {
        return vibeCommentMapper.countCommentsByPostId(postId);
    }

    @Override
    public boolean deleteComment(Long commentId, Long userId, String role) {
        VibeComment comment = vibeCommentMapper.selectById(commentId);
        if (comment == null) {
            return false;
        }
        boolean isAdmin = AdminGuard.isAdmin(role);
        if (!isAdmin && !comment.getUserId().equals(userId)) {
            throw BusinessException.forbidden("Only the author or an admin can delete this comment.");
        }
        boolean deleted = vibeCommentMapper.deleteById(commentId) > 0;
        if (deleted && comment.getPostId() != null) {
            vibePostMapper.recalculateCommentCount(comment.getPostId());
        }
        return deleted;
    }
}
