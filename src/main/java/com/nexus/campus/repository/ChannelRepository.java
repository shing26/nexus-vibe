package com.nexus.campus.repository;

import com.nexus.campus.entity.Channel;

import java.util.Optional;

public interface ChannelRepository {

    Optional<Channel> findById(Integer id);
}
