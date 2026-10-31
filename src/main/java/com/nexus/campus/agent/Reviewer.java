package com.nexus.campus.agent;

import com.nexus.campus.entity.VibePost;

/**
 * Produces review verdicts for claimed posts — the second ADR-0012 seam,
 * alongside {@link ReviewPolicy}. The trigger rule decides whether a post is
 * subject to review; the reviewer decides what the verdict means.
 *
 * <p>What stays in core, deliberately: the lease, the validity gate
 * ({@link ReviewValidity}), and everything recording a verdict does — the log
 * row, the AI comment, the score writeback, the supersede of stale comments.
 * A replacement reviewer produces data, not side effects.</p>
 */
public interface Reviewer {

    /** Identity stamped into ai_review_log.reviewer. */
    String identity();

    /** Whether this reviewer produces the verdict for the given post. */
    boolean claims(VibePost post);

    /**
     * Reviews a claimed post. Returns {@code null} when the LLM is
     * unavailable (core marks the post FAILED for reconciliation). A returned
     * verdict may still be invalid — core runs {@link ReviewValidity} before
     * recording anything, so an implementation cannot bypass the gate by
     * trusting its own output.
     */
    Verdict review(VibePost post);

    /**
     * The structured verdict plus the comment body it renders as. The field
     * names are shared by every reviewer on purpose: the persisted JSON keys,
     * the read path and the terminal all treat the three analysis fields
     * generically, so a new reviewer changes what they mean, not what they
     * are called.
     */
    final class Verdict {

        public final int score;
        public final String severity;
        public final String quality;
        public final String security;
        public final String suggestions;
        public final boolean approved;
        /** Rendered markdown body of the AI comment, sans the shared header. */
        public final String commentBody;
        /** The model's raw output as stored in ai_review_log.result_json. */
        public final String resultJson;

        public Verdict(int score, String severity, String quality, String security,
                       String suggestions, boolean approved, String commentBody, String resultJson) {
            this.score = score;
            this.severity = severity;
            this.quality = quality;
            this.security = security;
            this.suggestions = suggestions;
            this.approved = approved;
            this.commentBody = commentBody;
            this.resultJson = resultJson;
        }

        public int getScore() {
            return score;
        }

        public String getSeverity() {
            return severity;
        }

        public String getQuality() {
            return quality;
        }

        public String getSecurity() {
            return security;
        }

        public String getSuggestions() {
            return suggestions;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getCommentBody() {
            return commentBody;
        }

        public String getResultJson() {
            return resultJson;
        }
    }
}
