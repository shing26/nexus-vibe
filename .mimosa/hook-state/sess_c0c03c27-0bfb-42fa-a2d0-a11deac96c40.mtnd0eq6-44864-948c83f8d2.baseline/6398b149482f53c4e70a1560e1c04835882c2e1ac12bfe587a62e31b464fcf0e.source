package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.entity.Channel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChannelMapper extends BaseMapper<Channel> {

    @Select("SELECT * FROM vibe_channel WHERE status = 1 ORDER BY sort_order ASC")
    List<Channel> selectAllActive();

    @Select("SELECT * FROM vibe_channel WHERE slug = #{slug} AND status = 1")
    Channel selectBySlug(@Param("slug") String slug);
}


