package com.nexus.campus.repository.impl;

import com.nexus.campus.entity.SysUser;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.repository.SysUserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisSysUserRepository implements SysUserRepository {

    private final SysUserMapper mapper;

    public MyBatisSysUserRepository(SysUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SysUser> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public void update(SysUser user) {
        mapper.updateById(user);
    }
}
