package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.PasswordChangeRequest;
import com.nexus.campus.dto.ProfileUpdateRequest;
import com.nexus.campus.dto.UserProfileSummary;
import com.nexus.campus.dto.UserPublicVo;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.service.SysUserService;
import com.nexus.campus.service.UserProfileSummaryService;
import com.nexus.campus.service.impl.SysUserServiceImpl;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private UserProfileSummaryService userProfileSummaryService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/search")
    public ApiResponse<List<UserPublicVo>> searchUsers(@RequestParam String keyword) {
        List<SysUser> users = sysUserMapper.searchByKeyword(keyword);
        List<UserPublicVo> vos = users.stream().map(this::convertToPublicVo).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserPublicVo> getUserById(@PathVariable Long id) {
        SysUser user = sysUserService.getUserById(id);
        if (user == null) {
            return ApiResponse.notFound("User not found.");
        }
        return ApiResponse.success(convertToPublicVo(user));
    }

    @GetMapping("/{id}/summary")
    public ApiResponse<UserProfileSummary> getUserProfileSummary(@PathVariable Long id) {
        UserProfileSummary summary = userProfileSummaryService.getSummary(id);
        if (summary == null) {
            return ApiResponse.notFound("User not found.");
        }
        return ApiResponse.success(summary);
    }

    private UserPublicVo convertToPublicVo(SysUser user) {
        UserPublicVo vo = new UserPublicVo();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @GetMapping("/profile")
    public ApiResponse<SysUser> getProfile(@RequestAttribute("currentUserId") Long userId) {
        SysUser user = sysUserService.getUserById(userId);
        if (user == null) {
            return ApiResponse.notFound("User not found.");
        }
        user.setPassword(null);
        return ApiResponse.success(user);
    }

    @PutMapping("/profile")
    public ApiResponse<SysUser> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            @RequestAttribute("currentUserId") Long userId) {
        SysUser user = sysUserService.getUserById(userId);
        if (user == null) {
            return ApiResponse.notFound("User not found.");
        }
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null && !request.getAvatar().isBlank()) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatar(request.getAvatarUrl());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        sysUserService.updateUser(user);
        user.setPassword(null);
        return ApiResponse.success("Profile updated.", user);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            @RequestAttribute("currentUserId") Long userId) {
        SysUser user = sysUserService.getUserById(userId);
        if (user == null) {
            return ApiResponse.notFound("User not found.");
        }
        boolean oldPasswordOk = passwordEncoder.matches(request.getOldPassword(), user.getPassword());
        if (!oldPasswordOk && SysUserServiceImpl.isLegacySha256(user.getPassword())) {
            oldPasswordOk = user.getPassword()
                    .equalsIgnoreCase(SysUserServiceImpl.encryptPassword(request.getOldPassword()));
        }
        if (!oldPasswordOk) {
            return ApiResponse.error(400, "Old password is incorrect.");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserService.updateUser(user);
        return ApiResponse.successMessage("Password changed successfully.");
    }
}
