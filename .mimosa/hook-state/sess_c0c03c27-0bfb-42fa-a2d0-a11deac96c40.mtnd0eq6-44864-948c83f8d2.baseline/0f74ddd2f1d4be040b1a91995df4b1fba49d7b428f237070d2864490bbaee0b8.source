package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.entity.VibePostTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VibePostTagMapper extends BaseMapper<VibePostTag> {

    int insertBatch(@Param("postId") Long postId, @Param("tagIds") List<Integer> tagIds);
}
