package com.nexus.campus.service;

import com.nexus.campus.entity.SysMessage;

import java.util.List;

public interface SysMessageService {

    SysMessage sendMessage(Long fromUserId, Long toUserId, String content, Integer type);

    List<SysMessage> getMessagesByUserId(Long userId);

    int countUnreadMessages(Long userId);

    boolean markAsRead(Long messageId, Long userId);

    boolean markAllAsRead(Long userId);
}
