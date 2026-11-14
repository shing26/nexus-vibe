package com.nexus.campus.agent;

import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.repository.AiReviewLogRepository;
import com.nexus.campus.repository.VibeCommentRepository;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.service.SysMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Records review verdicts. Since ADR-0012's reviewer seam was implemented,
 * the LLM pipeline lives in {@link Reviewer} implementations; this class owns
 * everything that happens to a verdict once it exists — the validity gate,
 * the log row, the AI comment, the score writeback, the supersede of stale
 * comments, and the failure notifications. That split is the point: "what a
 * verdict must satisfy before it may be recorded" stays in core, where no
 * replacement reviewer can weaken it.
 */
@Slf4j
@Service
public class AiReviewService {

    /** AiAgent system account that posts review comments. */
    private static final long AI_AGENT_USER_ID = 999L;

    /** First line of every AI review comment — used to find stale ones. */
    private static final String REVIEW_COMMENT_MARKER = "## AI Code Review";

    @Autowired
    private VibePostRepository posts;

    @Autowired
    private AiReviewLogRepository reviewLogs;

    @Autowired
    private VibeCommentRepository comments;

    @Autowired
    private SysMessageService sysMessageService;

    @Autowired
    private CampusAiProperties aiProperties;

    /**
     * Full recording pipeline for one claimed post. A null verdict means the
     * LLM was unavailable; an invalid verdict degrades after the reviewer's
     * own retry ladder — both mark the post FAILED for reconciliation, the
     * difference is only what the log row records.
     *
     * <p>{@code attempts} is the attempt count the listener's claim consumed.
     * When it has reached the configured maximum, this is the last attempt the
     * post will ever get — {@code tryClaimReview} refuses any further claim —
     * so the degraded branches owe the author the terminal "no more retries"
     * notification here. This path returns normally instead of throwing, so
     * neither the listener's catch branch nor the reconciliation sweep would
     * ever notify for it.</p>
     */
    public void reviewPost(VibePost post, Reviewer reviewer, Long authorId, boolean retried, int attempts) {
        Long postId = post.getId();
        String title = post.getTitle();

        Reviewer.Verdict verdict = reviewer.review(post);

        if (verdict == null) {
            log.warn("LLM unavailable for post {}; review marked FAILED for reconciliation", postId);
            saveReviewLog(postId, reviewer.identity(), null, "unavailable", 0);
            markPost(postId, AiReviewStatus.FAILED);
            notifyReviewFailed(postId, authorId, title, "LLM 暂时不可用", retried, isFinalAttempt(attempts));
            return;
        }

        if (!ReviewValidity.isValid(verdict)) {
            log.warn("Review result for post {} failed validation; degrading silently", postId);
            saveReviewLog(postId, reviewer.identity(), verdict.getResultJson(), "unknown", 0);
            markPost(postId, AiReviewStatus.FAILED);
            notifyReviewFailed(postId, authorId, title, "评审结果未通过质量校验", retried, isFinalAttempt(attempts));
            return;
        }

        // Save review log
        saveReviewLog(postId, reviewer.identity(), verdict.getResultJson(),
                verdict.getSeverity(), verdict.isApproved() ? 1 : 0);

        // A new review supersedes the previous one: hide stale AI comments so
        // the thread never shows contradictory scores (full history stays in
        // ai_review_log and the agent-logs dashboard).
        supersedePreviousReviewComments(postId);

        // Post AI comment on the post
        createReviewComment(postId, verdict.getCommentBody());

        // Update vibe_post with ai_review_score and mark as reviewed
        try {
            VibePost update = new VibePost();
            update.setId(postId);
            update.setAiReviewed(AiReviewStatus.REVIEWED.getCode());
            update.setAiReviewScore(verdict.getScore());
            posts.update(update);
            log.info("AI review score {} from {} written back to vibe_post {}",
                     verdict.getScore(), reviewer.identity(), postId);
        } catch (Exception e) {
            log.warn("Failed to update ai_review_score for post {}: {}", postId, e.getMessage());
        }
    }

    /**
     * Author notification when a review degrades. Three regimes, one per
     * attempt budget state:
     *
     * <ul>
     *   <li><b>final attempt</b> — the budget is spent and no retry path can
     *       ever claim this post again, so the author gets the terminal
     *       "已停止自动重试" notification once, even when this last attempt
     *       was a reconciled retry that would otherwise stay silent;</li>
     *   <li><b>retried mid-budget</b> — silent: the author was told once and
     *       each retry cycle would otherwise spam the message inbox;</li>
     *   <li><b>first failure</b> — the informational "系统会自动重试" notice.</li>
     * </ul>
     *
     * <p>The terminal wording matches {@code AiReviewReconcileTask.notifyBudgetExhausted}
     * and the listener's {@code notifyBudgetExhausted}: one sentence, one tone,
     * whichever path delivers it.</p>
     */
    private void notifyReviewFailed(Long postId, Long authorId, String title, String reason,
                                    boolean retried, boolean finalAttempt) {
        if (authorId == null) {
            return;
        }
        if (finalAttempt) {
            try {
                sysMessageService.sendMessage(SysMessage.FROM_SYSTEM, authorId,
                        "你的帖子《" + title + "》的 AI 评审连续 " + aiProperties.getReview().getMaxAttempts()
                                + " 次失败，已停止自动重试。内容本身不受影响；如需重新评审，请联系管理员。",
                        SysMessage.TYPE_SYSTEM);
            } catch (Exception e) {
                log.warn("Failed to notify author {} about exhausted review budget on post {}: {}",
                         authorId, postId, e.getMessage());
            }
            return;
        }
        // Reconciliation retries are silent: the author was told once and each
        // retry cycle would otherwise spam the message inbox.
        if (retried) {
            return;
        }
        try {
            sysMessageService.sendMessage(SysMessage.FROM_SYSTEM, authorId,
                    "你的帖子《" + title + "》的 AI 评审暂时失败（" + reason + "），系统会自动重试。",
                    SysMessage.TYPE_SYSTEM);
        } catch (Exception e) {
            log.warn("Failed to notify author {} about review failure on post {}: {}",
                     authorId, postId, e.getMessage());
        }
    }

    /**
     * True when this attempt consumed the last budget slot. The claim refuses
     * at {@code attempts >= maxAttempts}, so an attempt above the maximum
     * cannot occur through the listener; comparing with {@code >=} keeps the
     * final-attempt branch safe even if a caller miscounts.
     */
    private boolean isFinalAttempt(int attempts) {
        return attempts >= aiProperties.getReview().getMaxAttempts();
    }

    /**
     * Marks a post's ai_reviewed state without touching other columns.
     */
    private void markPost(Long postId, AiReviewStatus status) {
        try {
            VibePost post = new VibePost();
            post.setId(postId);
            post.setAiReviewed(status.getCode());
            posts.update(post);
        } catch (Exception e) {
            log.warn("Failed to mark post {} as {}: {}", postId, status, e.getMessage());
        }
    }

    /**
     * Hides earlier AI review comments for the post (status=0 removes them
     * from the thread; the comment list filters on status=1). Best-effort.
     */
    private void supersedePreviousReviewComments(Long postId) {
        try {
            comments.hideAiReviewComments(postId, AI_AGENT_USER_ID, REVIEW_COMMENT_MARKER);
        } catch (Exception e) {
            log.warn("Failed to supersede previous AI comments for post {}: {}", postId, e.getMessage());
        }
    }

    /**
     * Creates a comment on the post as the AiAgent system user (id=999).
     */
    public void createReviewComment(Long postId, String body) {
        try {
            VibeComment comment = new VibeComment();
            comment.setPostId(postId);
            comment.setUserId(AI_AGENT_USER_ID);
            comment.setParentId(0L);
            comment.setTargetId(0L);
            comment.setContent(REVIEW_COMMENT_MARKER + "\n\n" + body);
            comment.setStatus(1);
            comments.insert(comment);
            posts.recalculateCommentCount(postId);
            log.info("AI review comment posted for post {}", postId);
        } catch (Exception e) {
            log.warn("Failed to post AI review comment for post {}: {}", postId, e.getMessage());
        }
    }

    // ---- private helpers ----

    private void saveReviewLog(Long postId, String reviewer, String resultJson,
                               String severity, int isApproved) {
        try {
            AiReviewLog logEntry = new AiReviewLog();
            logEntry.setPostId(postId);
            logEntry.setReviewer(reviewer);
            logEntry.setResultJson(resultJson);
            logEntry.setSeverity(severity);
            logEntry.setIsApproved(isApproved);
            logEntry.setCreatedAt(LocalDateTime.now());
            reviewLogs.insert(logEntry);
        } catch (Exception e) {
            log.warn("Failed to save AI review log for post {}: {}", postId, e.getMessage());
        }
    }
}
