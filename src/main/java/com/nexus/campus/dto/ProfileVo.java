package com.nexus.campus.dto;

import com.nexus.campus.entity.SysUser;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * The account a user reads back about themselves: every {@link SysUser} field
 * except the password hash. Mapping is field by field rather than reflective on
 * purpose — {@code BeanUtils.copyProperties} would start shipping any column
 * added to the table, which is the leak this class exists to close.
 */
@Data
public class ProfileVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private String nickname;
    private String avatar;
    private String bio;
    private String role;
    private Integer corePower;
    private Integer level;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static ProfileVo from(SysUser user) {
        if (user == null) {
            return null;
        }
        ProfileVo vo = new ProfileVo();
        vo.id = user.getId();
        vo.username = user.getUsername();
        vo.email = user.getEmail();
        vo.nickname = user.getNickname();
        vo.avatar = user.getAvatar();
        vo.bio = user.getBio();
        vo.role = user.getRole();
        vo.corePower = user.getCorePower();
        vo.level = user.getLevel();
        vo.status = user.getStatus();
        vo.createTime = user.getCreateTime();
        vo.updateTime = user.getUpdateTime();
        return vo;
    }
}
