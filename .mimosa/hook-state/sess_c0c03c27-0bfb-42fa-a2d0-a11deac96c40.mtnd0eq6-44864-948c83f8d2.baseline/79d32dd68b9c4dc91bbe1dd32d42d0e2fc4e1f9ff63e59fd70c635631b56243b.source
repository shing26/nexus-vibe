package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {

    @Select("SELECT m.*, u.nickname as fromUserName, u.avatar as fromUserAvatar " +
            "FROM sys_message m " +
            "LEFT JOIN sys_user u ON m.from_user_id = u.id " +
            "WHERE m.to_user_id = #{userId} " +
            "ORDER BY m.create_time DESC")
    List<SysMessage> selectMessagesByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_message WHERE to_user_id = #{userId} AND is_read = 0")
    int countUnreadMessages(@Param("userId") Long userId);

    @Update("UPDATE sys_message SET is_read = 1 WHERE to_user_id = #{userId} AND is_read = 0")
    int markAllAsRead(@Param("userId") Long userId);
}
