package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE status = 1 AND (username LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%'))")
    List<SysUser> searchByKeyword(@Param("keyword") String keyword);

    @Select("SELECT DISTINCT u.* FROM sys_user u " +
            "INNER JOIN vibe_post p ON p.user_id = u.id " +
            "WHERE p.status = 1 AND p.create_time >= DATEADD('DAY', -7, CURRENT_TIMESTAMP) " +
            "ORDER BY p.create_time DESC " +
            "LIMIT #{limit}")
    List<SysUser> selectRecentActiveUsers(@Param("limit") int limit);
}
