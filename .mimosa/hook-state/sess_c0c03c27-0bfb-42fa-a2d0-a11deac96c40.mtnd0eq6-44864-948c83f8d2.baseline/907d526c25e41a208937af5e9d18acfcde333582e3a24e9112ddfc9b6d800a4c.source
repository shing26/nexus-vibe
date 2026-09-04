package com.nexus.campus.agent;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ai_review_log")
public class AiReviewLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long postId;

    private String reviewer;

    private String resultJson;

    private String severity;

    private Integer isApproved;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
