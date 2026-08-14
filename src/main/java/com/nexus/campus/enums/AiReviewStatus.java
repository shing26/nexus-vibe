package com.nexus.campus.enums;

/**
 * AI review state of a {@code vibe_post} row, matching the {@code ai_reviewed} column.
 */
public enum AiReviewStatus {

    NOT_REVIEWED(0, "Not reviewed"),
    REVIEWED(1, "Reviewed"),
    REVIEWING(2, "Review in progress");

    private final int code;
    private final String label;

    AiReviewStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
