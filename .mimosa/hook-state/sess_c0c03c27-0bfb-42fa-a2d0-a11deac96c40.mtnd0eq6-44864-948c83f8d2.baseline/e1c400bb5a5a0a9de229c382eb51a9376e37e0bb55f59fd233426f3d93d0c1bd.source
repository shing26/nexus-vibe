package com.nexus.campus.enums;

/**
 * Lifecycle status of a {@code vibe_post} row, matching the {@code status} column.
 */
public enum PostStatus {

    ACTIVE(1, "Active"),
    PENDING_REVIEW(2, "Pending Audit"),
    REJECTED(3, "Rejected");

    private final int code;
    private final String label;

    PostStatus(int code, String label) {
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
