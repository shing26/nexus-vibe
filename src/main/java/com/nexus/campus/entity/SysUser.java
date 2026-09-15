package com.nexus.campus.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(condition = SqlCondition.EQUAL)
    private String username;

    private String email;

    /**
     * Write-only: the hash is an input to the service layer and must never come
     * back out in a response body. Before this annotation the protection was
     * three hand-written setPassword(null) calls in three controllers, which a
     * fourth endpoint returning SysUser could silently skip.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String nickname;

    @TableField(fill = FieldFill.INSERT)
    private String avatar;

    private String bio;

    private String role;

    private Integer corePower;

    private Integer level;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
