package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One point in a user's public evidence index: the latest completed code
 * review of one of their posts. Deliberately metadata only — no resultJson or
 * findings text — because the point of this surface is to be traceable, not to
 * republish the review body.
 */
@Data
public class UserReviewEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** {@code ai_review_log.id} of the row this entry was read from (S4-3 provenance anchor). */
    private Long logId;

    private Long postId;

    private String postTitle;

    private Integer score;

    private String severity;

    private Boolean isApproved;

    private LocalDateTime reviewedAt;

    /** Optional timing instrument written into resultJson; null on rows that predate it. */
    private Integer durationMs;
}
