package com.nexus.campus.service.impl;

import com.nexus.campus.entity.Channel;
import com.nexus.campus.mapper.ChannelMapper;
import com.nexus.campus.service.ChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChannelServiceImpl implements ChannelService {

    @Autowired
    private ChannelMapper channelMapper;

    @Override
    @Cacheable(value = "channels", key = "'active'")
    public List<Channel> getAllActiveChannels() {
        return channelMapper.selectAllActive();
    }

    @Override
    public Channel getBySlug(String slug) {
        return channelMapper.selectBySlug(slug);
    }

    @Override
    public Channel getCategoryById(Integer id) {
        return channelMapper.selectById(id);
    }
}
