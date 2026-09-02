package com.nexus.campus.entity;

import com.baomidou.mybatisplus.annotation.*;
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
