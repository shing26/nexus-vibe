package com.nexus.campus.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Profile update payload. Constraints mirror the sys_user columns
 * (nickname varchar(50) NOT NULL, avatar/avatar_url/bio varchar(255)) so an
 * oversized value fails validation with a 400 instead of surfacing as a
 * MySQL MysqlDataTruncation 500. Fields are nullable on purpose: the
 * controller performs partial updates, skipping absent/blank fields.
 */
@Data
public class ProfileUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 50, message = "Nickname must not exceed 50 characters.")
    private String nickname;

    @Size(max = 255, message = "Avatar must not exceed 255 characters.")
    private String avatar;

    @Size(max = 255, message = "Avatar URL must not exceed 255 characters.")
    private String avatarUrl;

    @Size(max = 255, message = "Bio must not exceed 255 characters.")
    private String bio;
}
