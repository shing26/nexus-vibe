package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.dto.TagUsageVo;
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

    /**
     * Tags a public directory can show with confidence: joined to the posts
     * that use them, but only posts whose status is ACTIVE — a post sitting in
     * the audit queue must not make its tag look busier than the public page
     * will show. Ordered by usage so the page needs no client-side sort.
     */
    @Select("SELECT t.id AS tagId, t.name AS name, COUNT(p.id) AS postCount " +
            "FROM vibe_tag t " +
            "INNER JOIN vibe_post_tag pt ON pt.tag_id = t.id " +
            "INNER JOIN vibe_post p ON p.id = pt.post_id AND p.status = 1 " +
            "WHERE t.status = 1 " +
            "GROUP BY t.id, t.name " +
            "ORDER BY postCount DESC, t.name ASC")
    List<TagUsageVo> selectActiveTagsWithUsage();
}
