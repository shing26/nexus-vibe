package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.entity.VibeTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VibeTagMapper extends BaseMapper<VibeTag> {

    @Select("SELECT t.* FROM vibe_tag t " +
            "INNER JOIN vibe_post_tag pt ON t.id = pt.tag_id " +
            "WHERE pt.post_id = #{postId}")
    List<VibeTag> selectTagsByPostId(@Param("postId") Long postId);

    @Select("SELECT * FROM vibe_tag WHERE status = 1")
    List<VibeTag> selectActiveTags();
}
