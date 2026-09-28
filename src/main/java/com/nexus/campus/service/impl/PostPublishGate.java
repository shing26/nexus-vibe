package com.nexus.campus.service.impl;

import com.nexus.campus.agent.ReviewPolicy;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.dto.PostAuditResult;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.enums.PostStatus;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.service.SensitiveWordService;
import org.springframework.stereotype.Component;

/**
 * The publish-time decisions that creating and editing a post have to make
 * identically: what the sensitive-word filter does to the visible text, and
 * which agents the post is handed to afterwards.
 *
 * <p>Those two sequences used to sit in both {@code PostCreationService} and
 * {@code PostEditService}, so adding an agent, moving the DFA, or changing when
 * {@code REVIEWING} gets pre-written meant editing two files or letting the two
 * paths drift. The differences that are real stay in the callers, as the two
 * named entry points below.</p>
 */
@Component
public class PostPublishGate {

    private final SensitiveWordService sensitiveWords;
    private final VibePostRepository posts;
    private final PostAgentEventPublisher agentEvents;
    private final ReviewPolicy reviewPolicy;
    private final CampusAiProperties aiProperties;

    public PostPublishGate(SensitiveWordService sensitiveWords,
                           VibePostRepository posts,
                           PostAgentEventPublisher agentEvents,
                           ReviewPolicy reviewPolicy,
                           CampusAiProperties aiProperties) {
        this.sensitiveWords = sensitiveWords;
        this.posts = posts;
        this.agentEvents = agentEvents;
        this.reviewPolicy = reviewPolicy;
        this.aiProperties = aiProperties;
    }

    /** Which classes of word the DFA found in the post's visible text. */
    public record AuditVerdict(boolean critical, boolean sensitive) {
    }

    /**
     * Runs the DFA over the post's title and content and rewrites both when the
     * filter masks anything.
     *
     * <p>The caller owns what a verdict means for status, because the two paths
     * genuinely differ: creation publishes a critically-flagged post straight to
     * PENDING_REVIEW, while an edit only refuses to reactivate a post that is
     * already hidden.</p>
     */
    public AuditVerdict applySensitiveWordPolicy(VibePost post) {
        PostAuditResult titleAudit = sensitiveWords.checkText(post.getTitle());
        PostAuditResult contentAudit = sensitiveWords.checkText(post.getContent());
        boolean critical = titleAudit.isContainsCritical() || contentAudit.isContainsCritical();
        boolean sensitive = titleAudit.isContainsSensitive() || contentAudit.isContainsSensitive();
        if (sensitive) {
            post.setTitle(titleAudit.getFilteredContent());
            post.setContent(contentAudit.getFilteredContent());
        }
        return new AuditVerdict(critical, sensitive);
    }

    /** A brand-new post is new to both agents. */
    public void dispatchForNewPost(VibePost post, Long userId) {
        if (aiProperties.getReview().isEnabled() && reviewPolicy.shouldReview(post)) {
            startReview(post, userId);
        }
        if (aiProperties.getSafety().isEnabled() && isActive(post)) {
            agentEvents.publishSafety(post, userId);
        }
    }

    /**
     * On an edit only genuinely changed text is resubmitted. A review is worth
     * re-running only when the content changed; safety also re-runs for a
     * title-only change, because the title is classified too.
     */
    public void dispatchForEditedPost(VibePost post, Long userId,
                                      boolean titleChanged, boolean contentChanged) {
        if (contentChanged && aiProperties.getReview().isEnabled() && reviewPolicy.shouldReview(post)) {
            startReview(post, userId);
        }
        if ((titleChanged || contentChanged) && aiProperties.getSafety().isEnabled() && isActive(post)) {
            agentEvents.publishSafety(post, userId);
        }
    }

    /**
     * REVIEWING is written before the event goes out, so a crash between the two
     * leaves the post recoverable by reconciliation rather than silently
     * unreviewed.
     */
    private void startReview(VibePost post, Long userId) {
        post.setAiReviewed(AiReviewStatus.REVIEWING.getCode());
        posts.update(post);
        agentEvents.publishReview(post, userId);
    }

    private static boolean isActive(VibePost post) {
        return post.getStatus() != null && post.getStatus() == PostStatus.ACTIVE.getCode();
    }
}
