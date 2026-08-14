package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result returned by {@link com.nexus.campus.service.SensitiveWordService#checkText(String)}.
 *
 * <p>Carries the filtered (HTML-safe) content alongside the audit verdict so
 * callers can decide whether to publish, shunt to a moderation queue, or
 * reject entirely.</p>
 */
@Data
public class PostAuditResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Audit verdict. */
    private AuditStatus status;

    /** The input text with every matched keyword replaced by {@code [敏感词]}. */
    private String filteredContent;

    /** Whether at least one regular sensitive word was matched. */
    private boolean containsSensitive;

    /** Whether at least one critical / political keyword was matched. */
    private boolean containsCritical;

    /** Total number of distinct keyword matches found. */
    private int sensitiveWordCount;

    /** List of matched keywords (for logging or admin review). */
    private List<String> matchedKeywords;

    public PostAuditResult() {
        this.status = AuditStatus.PASS;
        this.filteredContent = "";
        this.matchedKeywords = new ArrayList<>();
    }

    // ──────────────────────────────────────────────
    // Factory helpers
    // ──────────────────────────────────────────────

    public static PostAuditResult pass(String content) {
        PostAuditResult r = new PostAuditResult();
        r.status = AuditStatus.PASS;
        r.filteredContent = content;
        r.containsSensitive = false;
        r.containsCritical = false;
        r.sensitiveWordCount = 0;
        return r;
    }

    public static PostAuditResult pendingAudit(String filteredContent, int wordCount, List<String> keywords) {
        PostAuditResult r = new PostAuditResult();
        r.status = AuditStatus.PENDING_AUDIT;
        r.filteredContent = filteredContent;
        r.containsSensitive = true;
        r.containsCritical = true;
        r.sensitiveWordCount = wordCount;
        r.matchedKeywords = keywords;
        return r;
    }

    public static PostAuditResult sensitiveOnly(String filteredContent, int wordCount, List<String> keywords) {
        PostAuditResult r = new PostAuditResult();
        r.status = AuditStatus.PASS;
        r.filteredContent = filteredContent;
        r.containsSensitive = true;
        r.containsCritical = false;
        r.sensitiveWordCount = wordCount;
        r.matchedKeywords = keywords;
        return r;
    }

    // ──────────────────────────────────────────────
    // AuditStatus enum
    // ──────────────────────────────────────────────

    public enum AuditStatus {
        /** Content is clean — can be published directly. */
        PASS,

        /** Content contains critical keywords — must wait for manual admin review. */
        PENDING_AUDIT,

        /** Content has been rejected by an admin (set externally, not by the service). */
        REJECTED
    }
}
