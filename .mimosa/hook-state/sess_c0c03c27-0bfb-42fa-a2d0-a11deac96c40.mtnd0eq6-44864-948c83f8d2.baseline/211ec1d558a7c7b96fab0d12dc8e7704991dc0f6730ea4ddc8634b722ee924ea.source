package com.nexus.campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_message")
public class SysMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** fromUserId sentinel for system-generated messages (AiAgent / moderation). */
    public static final long FROM_SYSTEM = 0L;
    /** sys_message type codes as used by sendMessage(). */
    public static final int TYPE_COMMENT = 2;
    public static final int TYPE_SYSTEM = 3;
    public static final int TYPE_AI_REVIEW = 4;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    private String content;

    private Integer type;

    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String fromUserName;

    @TableField(exist = false)
    private String fromUserAvatar;
}
