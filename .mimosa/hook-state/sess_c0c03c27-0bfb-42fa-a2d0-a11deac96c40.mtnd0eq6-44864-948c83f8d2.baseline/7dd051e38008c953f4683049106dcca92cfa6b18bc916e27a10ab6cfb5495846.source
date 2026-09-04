package com.nexus.campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("vibe_post")
public class VibePost implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Integer categoryId;

    private String title;

    private String content;

    private String summary;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer status;

    /**
     * 0 = normal, 1 = pinned
     */
    private Integer isPinned;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * JSON array of detected code snippets
     */
    private String codeSnippets;

    /**
     * 0 = not reviewed, 1 = reviewed, 2 = in progress
     */
    private Integer aiReviewed;

    /**
     * AI review score (0-10)
     */
    private Integer aiReviewScore;

    /**
     * Estimated token count of the post content
     */
    private Integer tokenCount;

    @TableField(exist = false)
    private String authorName;

    private String postType;

    private String promptMetadata;

    private Long forkedFromId;

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private Integer versionCount;
}
