package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.entity.VibeComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VibeCommentMapper extends BaseMapper<VibeComment> {

    @Select("SELECT c.*, u.nickname as authorName, u.avatar as authorAvatar " +
            "FROM vibe_comment c " +
            "LEFT JOIN sys_user u ON c.user_id = u.id " +
            "WHERE c.post_id = #{postId} AND c.status = 1 " +
            "ORDER BY c.create_time ASC")
    List<VibeComment> selectCommentsByPostId(@Param("postId") Long postId);

    @Select("SELECT c.*, u.nickname as authorName " +
            "FROM vibe_comment c " +
            "LEFT JOIN sys_user u ON c.user_id = u.id " +
            "WHERE c.parent_id = #{parentId} AND c.status = 1 " +
            "ORDER BY c.create_time ASC")
    List<VibeComment> selectRepliesByParentId(@Param("parentId") Long parentId);

    @Select("SELECT COUNT(*) FROM vibe_comment WHERE post_id = #{postId} AND status = 1")
    int countCommentsByPostId(@Param("postId") Long postId);

    @Select("SELECT COUNT(*) FROM vibe_comment WHERE status = 1")
    long countTotalComments();

    @Select("SELECT COUNT(*) FROM vibe_comment WHERE user_id = #{userId} AND status = 1")
    long countCommentsByUserId(@Param("userId") Long userId);

    @Select("SELECT c.*, u.nickname as authorName, u.avatar as authorAvatar " +
            "FROM vibe_comment c " +
            "LEFT JOIN sys_user u ON c.user_id = u.id " +
            "WHERE c.user_id = #{userId} AND c.status = 1 " +
            "ORDER BY c.create_time DESC, c.id DESC LIMIT 10")
    List<VibeComment> selectLatestActiveCommentsByUserId(@Param("userId") Long userId);
}
