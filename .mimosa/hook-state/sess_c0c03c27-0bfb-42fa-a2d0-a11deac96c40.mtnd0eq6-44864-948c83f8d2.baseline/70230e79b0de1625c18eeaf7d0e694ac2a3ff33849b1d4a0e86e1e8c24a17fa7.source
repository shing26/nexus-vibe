package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.JwtResponse;
import com.nexus.campus.dto.LoginRequest;
import com.nexus.campus.dto.RefreshTokenRequest;
import com.nexus.campus.dto.RegisterRequest;
import com.nexus.campus.entity.SysUser;
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
        try {
            JwtResponse jwtResponse = sysUserService.login(request);
            return ApiResponse.success("Neural link established. Welcome back, " + jwtResponse.getNickname() + ".", jwtResponse);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, "Authentication failed: " + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<JwtResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            JwtResponse jwtResponse = sysUserService.register(request);
            return ApiResponse.success("User registered. Welcome to the Nexus, " + jwtResponse.getNickname() + ".", jwtResponse);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, "Registration failed: " + e.getMessage());
        }
    }

    @GetMapping("/profile")
    public ApiResponse<?> getProfile(@RequestAttribute("currentUserId") Long userId) {
        SysUser user = sysUserService.getUserById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return ApiResponse.success(user);
    }

    @PostMapping("/refresh")
    public ApiResponse<JwtResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();

        if (!jwtUtil.validateRefreshToken(rawToken)) {
            return ApiResponse.error(401, "Refresh token is invalid or expired.");
        }

        Long userId = jwtUtil.getUserIdFromRefreshToken(rawToken);
        SysUser user = sysUserService.getUserById(userId);

        if (user == null) {
            return ApiResponse.error(401, "User not found.");
        }

        if (user.getStatus() == 0) {
            return ApiResponse.error(403, "Account has been deactivated.");
        }

        String newToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        JwtResponse jwtResponse = new JwtResponse(newToken, newRefreshToken, user.getId(),
                user.getUsername(), user.getNickname(), user.getRole(), user.getAvatar(),
                user.getCorePower(), user.getLevel());

        return ApiResponse.success("Token refreshed successfully.", jwtResponse);
    }
}
