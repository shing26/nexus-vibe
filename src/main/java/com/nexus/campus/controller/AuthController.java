package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.JwtResponse;
import com.nexus.campus.dto.LoginRequest;
import com.nexus.campus.dto.RefreshTokenRequest;
import com.nexus.campus.dto.RegisterRequest;
import com.nexus.campus.dto.ProfileVo;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.service.SysUserService;
import com.nexus.campus.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ApiResponse<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        // No catch here any more. Catching RuntimeException made every failure on
        // this path - a MySQL outage included - answer 401 and quote the internal
        // message back at an anonymous caller, so a database problem read as
        // "wrong password" to the user, to the logs, and to the 5xx alert.
        JwtResponse jwtResponse = sysUserService.login(request);
        return ApiResponse.success("Neural link established. Welcome back, " + jwtResponse.getNickname() + ".", jwtResponse);
    }

    @PostMapping("/register")
    public ApiResponse<JwtResponse> register(@Valid @RequestBody RegisterRequest request) {
        JwtResponse jwtResponse = sysUserService.register(request);
        return ApiResponse.success("User registered. Welcome to the Nexus, " + jwtResponse.getNickname() + ".", jwtResponse);
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileVo> getProfile(@RequestAttribute("currentUserId") Long userId) {
        SysUser user = sysUserService.getUserById(userId);
        // A deleted-but-still-tokened user used to answer 200 with data:null; the
        // null check lives in ProfileVo.from so that shape is unchanged.
        return ApiResponse.success(ProfileVo.from(user));
    }

    @PostMapping("/refresh")
    public ApiResponse<JwtResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();

        if (!jwtUtil.validateRefreshToken(rawToken)) {
            throw BusinessException.unauthorized("Refresh token is invalid or expired.");
        }

        Long userId = jwtUtil.getUserIdFromRefreshToken(rawToken);
        SysUser user = sysUserService.getUserById(userId);

        if (user == null) {
            throw BusinessException.unauthorized("User not found.");
        }

        if (user.getStatus() == 0) {
            throw BusinessException.forbidden("Account has been deactivated.");
        }

        String newToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        JwtResponse jwtResponse = new JwtResponse(newToken, newRefreshToken, user.getId(),
                user.getUsername(), user.getNickname(), user.getRole(), user.getAvatar(),
                user.getCorePower(), user.getLevel());

        return ApiResponse.success("Token refreshed successfully.", jwtResponse);
    }
}
