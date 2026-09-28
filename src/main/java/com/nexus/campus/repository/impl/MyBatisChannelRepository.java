package com.nexus.campus.repository.impl;

import com.nexus.campus.entity.Channel;
import com.nexus.campus.mapper.ChannelMapper;
import com.nexus.campus.repository.ChannelRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisChannelRepository implements ChannelRepository {

    private final ChannelMapper mapper;

    public MyBatisChannelRepository(ChannelMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Channel> findById(Integer id) {
        return Optional.ofNullable(mapper.selectById(id));
    }
}
