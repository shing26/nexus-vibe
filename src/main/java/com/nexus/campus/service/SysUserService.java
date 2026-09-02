package com.nexus.campus.service;

import com.nexus.campus.dto.JwtResponse;
import com.nexus.campus.dto.LoginRequest;
import com.nexus.campus.dto.RegisterRequest;
import com.nexus.campus.entity.SysUser;

public interface SysUserService {

    JwtResponse login(LoginRequest request);

    JwtResponse register(RegisterRequest request);

    SysUser getUserById(Long id);

    SysUser getUserByUsername(String username);

    boolean updateUser(SysUser user);

    boolean addCorePower(Long userId, int points);

    /**
     * Stopgap password recovery until mail delivery exists: generates a random
     * temporary password, replaces the user's password with it and returns the
     * plaintext to the caller (the admin), who hands it to the user out-of-band.
     *
     * @return the generated temporary password
     */
    String resetPassword(String username);
}
