package com.nexus.campus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class JwtResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private String avatar;
    private Integer corePower;
    private Integer level;

    // Constructor without refreshToken (login/register use this)
    public JwtResponse(String token, Long userId, String username, String nickname, String role, String avatar, Integer corePower, Integer level) {
        this(token, null, userId, username, nickname, role, avatar, corePower, level);
    }

    // Full constructor including refreshToken
    public JwtResponse(String token, String refreshToken, Long userId, String username, String nickname, String role, String avatar, Integer corePower, Integer level) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.avatar = avatar;
        this.corePower = corePower;
        this.level = level;
    }
}
