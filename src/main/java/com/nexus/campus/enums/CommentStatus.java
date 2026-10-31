package com.nexus.campus.enums;

/**
 * VibeComment.status vocabulary. Comments have no rejected state of their own:
 * the comment-safety path hides (ADR-0017) with the same code the superseded
 * AI review comments already use, and prompt-injection holds reuse the audit
 * queue the DFA sensitive-word path created.
 */
public enum CommentStatus {

    /** Removed from every thread listing; also the superseded AI-comment code. */
    HIDDEN(0),

    VISIBLE(1),

    /** Held for a human audit decision (DFA critical hits, suspected prompt injection). */
    AUDIT_QUEUE(2);

    private final int code;

    CommentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
