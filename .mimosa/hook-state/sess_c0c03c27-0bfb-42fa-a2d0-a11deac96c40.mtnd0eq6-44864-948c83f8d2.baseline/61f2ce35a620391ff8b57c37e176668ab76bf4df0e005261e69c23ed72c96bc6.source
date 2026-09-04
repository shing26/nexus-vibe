package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.entity.PromptVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PromptVersionMapper extends BaseMapper<PromptVersion> {

    @Select("SELECT COALESCE(MAX(version), 0) FROM vibe_prompt_version WHERE post_id = #{postId} AND branch = #{branch}")
    int selectMaxVersion(@Param("postId") Long postId, @Param("branch") String branch);

    @Select("SELECT COUNT(*) FROM vibe_prompt_version WHERE post_id = #{postId}")
    long selectVersionCount(@Param("postId") Long postId);

    @Select("SELECT COUNT(*) FROM vibe_prompt_version WHERE created_by = #{userId}")
    long countVersionsByCreatedBy(@Param("userId") Long userId);

    @Select("SELECT * FROM vibe_prompt_version WHERE created_by = #{userId} " +
            "ORDER BY create_time DESC, id DESC LIMIT 10")
    List<PromptVersion> selectLatestVersionsByCreatedBy(@Param("userId") Long userId);
}
