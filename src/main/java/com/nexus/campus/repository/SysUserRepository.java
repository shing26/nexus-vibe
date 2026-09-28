package com.nexus.campus.repository;

import com.nexus.campus.entity.SysUser;

import java.util.Optional;

public interface SysUserRepository {

    Optional<SysUser> findById(Long id);

    void update(SysUser user);
}
