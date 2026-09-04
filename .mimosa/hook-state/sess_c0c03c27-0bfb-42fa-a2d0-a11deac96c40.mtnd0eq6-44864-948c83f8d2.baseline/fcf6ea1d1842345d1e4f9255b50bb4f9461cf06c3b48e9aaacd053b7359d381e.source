package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PostPageVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String authorName;
    private String authorAvatar;
    private Integer categoryId;
    private String categoryName;
    private String title;
    private String content;
    private String summary;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer status;
    private Integer aiReviewed;
    private Integer aiReviewScore;
    private LocalDateTime createTime;
    private String[] tags;
    
    /** Safety check severity from ai_review_log: critical, high, low, none, or null */
    private String safetySeverity;
    
    /** LLM classification: Prompt injection, Harmful content, Spam, Safe, or null */
    private String safetyClassification;
    
    /** Whether the safety check approved the post: 1 = clean/approved, 0 = flagged, null = unchecked */
    private Integer safetyIsApproved;

    private String postType;

    private String promptMetadata;

    private Long forkedFromId;

    private Integer versionCount;
}
