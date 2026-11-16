package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AiReviewDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long postId;

    private String reviewer;

    private Integer score;

    private String severity;

    private Boolean isApproved;

    private String codeQuality;

    private String securityConcerns;

    private String optimizationSuggestions;

    private LocalDateTime reviewedAt;

    /**
     * Wall-clock duration of the review attempt, read from
     * {@code result_json.durationMs}. Null on rows recorded before B8 added the
     * instrumentation, which is why the UI treats it as optional.
     */
    private Integer durationMs;
}
