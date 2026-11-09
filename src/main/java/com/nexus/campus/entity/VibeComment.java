package com.nexus.campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("vibe_comment")
public class VibeComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long postId;

    private Long userId;

    private Long parentId;

    private Long targetId;

    private String content;

    private Integer status;

    /**
     * Three-way keyword split: 0 other/unclassified, 1 question, 2 complaint,
     * 3 praise. Written once on insert by the deterministic classifier; rows
     * that predate the column stay 0.
     */
    private Integer sentiment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String authorName;

    @TableField(exist = false)
    private String authorAvatar;

    @TableField(exist = false)
    private String postTitle;
}
