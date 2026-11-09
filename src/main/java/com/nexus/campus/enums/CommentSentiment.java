package com.nexus.campus.enums;

/**
 * VibeComment.sentiment vocabulary: a three-way split of the conversation
 * (question / complaint / praise) plus a bucket for everything the rule
 * heuristic cannot place.
 *
 * <p>The value is derived once, at write time, by a deterministic keyword
 * classifier ({@code CommentSentimentClassifier}); it is a heuristic, not a
 * model's judgement. Comments that existed before the column was added stay
 * {@link #OTHER} — there is no backfill.
 */
public enum CommentSentiment {

    /** Unclassified, or a tie the heuristic refuses to call. Also every pre-existing row. */
    OTHER(0),

    QUESTION(1),

    COMPLAINT(2),

    PRAISE(3);

    private final int code;

    CommentSentiment(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
