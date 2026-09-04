package com.nexus.campus.service.impl;

import com.nexus.campus.dto.JwtResponse;
import com.nexus.campus.dto.LoginRequest;
import com.nexus.campus.dto.RegisterRequest;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.service.SysUserService;
import com.nexus.campus.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class SysUserServiceImpl implements SysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public JwtResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );

        if (user == null || !verifyPassword(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password.");
        }

        if (user.getStatus() == 0) {
            throw new RuntimeException("Account has been deactivated.");
        }

        // Upgrade legacy SHA-256 hashes to BCrypt on successful login.
        if (isLegacySha256(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            sysUserMapper.updateById(user);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        return new JwtResponse(token, refreshToken, user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), user.getAvatar(), user.getCorePower(), user.getLevel());
    }

    @Override
    public JwtResponse register(RegisterRequest request) {
        SysUser existing = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );
        if (existing != null) {
            throw new RuntimeException("Username already exists.");
        }
        SysUser existingEmail = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, request.getEmail())
        );
        if (existingEmail != null) {
            throw new RuntimeException("Email already exists.");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setRole("USER");
        user.setCorePower(0);
        user.setLevel(1);
        user.setStatus(1);

        sysUserMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        return new JwtResponse(token, refreshToken, user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), user.getAvatar(), user.getCorePower(), user.getLevel());
    }

    @Override
    public SysUser getUserById(Long id) {
        return sysUserMapper.selectById(id);
    }

    @Override
    public SysUser getUserByUsername(String username) {
        return sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
        );
    }

    @Override
    public boolean updateUser(SysUser user) {
        return sysUserMapper.updateById(user) > 0;
    }

    @Override
    public boolean addCorePower(Long userId, int points) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return false;
        user.setCorePower(user.getCorePower() + points);
        user.setLevel(calculateLevel(user.getCorePower()));
        return sysUserMapper.updateById(user) > 0;
    }

    @Override
    public String resetPassword(String username) {
        SysUser user = getUserByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found.");
        }
        String tempPassword = generateTempPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        if (sysUserMapper.updateById(user) <= 0) {
            throw new RuntimeException("Failed to reset password.");
        }
        return tempPassword;
    }

    /**
     * 12-char password satisfying the same complexity rule as registration:
     * upper, lower, digit. Prefix guarantees at least one of each class.
     */
    private String generateTempPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String all = upper + lower + digits + upper + lower + digits;
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        for (int i = 0; i < 9; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        return sb.toString();
    }

    private int calculateLevel(int corePower) {
        if (corePower < 100) return 1;
        if (corePower < 500) return 2;
        if (corePower < 1000) return 3;
        if (corePower < 5000) return 4;
        if (corePower < 10000) return 5;
        if (corePower < 50000) return 6;
        if (corePower < 100000) return 7;
        return 8;
    }

    public static String encryptPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Verifies a password against either the current BCrypt hash or a legacy
     * unsalted SHA-256 hash stored by older versions of the app.
     */
    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) return false;
        if (passwordEncoder.matches(rawPassword, storedHash)) return true;
        return isLegacySha256(storedHash) && storedHash.equalsIgnoreCase(encryptPassword(rawPassword));
    }

    public static boolean isLegacySha256(String storedHash) {
        return storedHash != null && storedHash.matches("[0-9a-fA-F]{64}");
    }
}
