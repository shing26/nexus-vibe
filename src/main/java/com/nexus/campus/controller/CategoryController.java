package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.ChannelStatsVo;
import com.nexus.campus.entity.Channel;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.ChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/channels")
public class CategoryController {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private VibePostMapper vibePostMapper;

    @GetMapping
    public ApiResponse<List<Channel>> getAllChannels() {
        return ApiResponse.success(channelService.getAllActiveChannels());
    }

    @GetMapping("/stats")
    public ApiResponse<List<ChannelStatsVo>> getChannelStats() {
        List<Channel> channels = channelService.getAllActiveChannels();
        List<ChannelStatsVo> stats = new ArrayList<>(channels.size());
        for (Channel channel : channels) {
            ChannelStatsVo vo = new ChannelStatsVo();
            vo.setId(channel.getId());
            vo.setSlug(channel.getSlug());
            vo.setPostCount(vibePostMapper.countActivePostsByCategory(channel.getId()));
            stats.add(vo);
        }
        return ApiResponse.success(stats);
    }
}
