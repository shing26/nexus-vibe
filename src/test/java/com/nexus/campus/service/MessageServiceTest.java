package com.nexus.campus.service;

import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.mapper.SysMessageMapper;
import com.nexus.campus.service.impl.SysMessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexus.campus.event.MessageEvent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private SysMessageMapper sysMessageMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SysMessageServiceImpl messageService;

    private final Long fromUserId = 100L;
    private final Long toUserId = 200L;
    private SysMessage sampleMessage;

    @BeforeEach
    void setUp() {
        sampleMessage = new SysMessage();
        sampleMessage.setId(1L);
        sampleMessage.setFromUserId(fromUserId);
        sampleMessage.setToUserId(toUserId);
        sampleMessage.setContent("Hello, this is a test message.");
        sampleMessage.setType(1);
        sampleMessage.setIsRead(0);
    }

    // ──────────────────────────────────────────────
    // sendMessage()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("sendMessage() should create and return a new message")
    void sendMessage() {
        SysMessage result = messageService.sendMessage(fromUserId, toUserId, "Hello!", 1);

        assertNotNull(result);
        assertEquals(fromUserId, result.getFromUserId());
        assertEquals(toUserId, result.getToUserId());
        assertEquals("Hello!", result.getContent());
        assertEquals(1, result.getType());
        assertEquals(0, result.getIsRead());

        // The implementation publishes a MessageEvent (not calling mapper directly)
        ArgumentCaptor<MessageEvent> captor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        MessageEvent event = captor.getValue();
        assertEquals(fromUserId, event.getSenderId());
        assertEquals(toUserId, event.getReceiverId());
        assertEquals("like", event.getMsgType());
        assertEquals("Hello!", event.getContent());
    }

    // ──────────────────────────────────────────────
    // getMessagesByUserId()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getMessagesByUserId() should return messages for the given user")
    void getMessagesByUserId() {
        SysMessage msg2 = new SysMessage();
        msg2.setId(2L);
        msg2.setFromUserId(fromUserId);
        msg2.setToUserId(toUserId);
        msg2.setContent("Second message.");
        msg2.setType(1);
        msg2.setIsRead(1);

        when(sysMessageMapper.selectMessagesByUserId(toUserId))
                .thenReturn(Arrays.asList(sampleMessage, msg2));

        List<SysMessage> messages = messageService.getMessagesByUserId(toUserId);

        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertEquals("Hello, this is a test message.", messages.get(0).getContent());
        assertEquals("Second message.", messages.get(1).getContent());
    }

    @Test
    @DisplayName("getMessagesByUserId() should return empty list when no messages exist")
    void getMessagesByUserIdEmpty() {
        when(sysMessageMapper.selectMessagesByUserId(toUserId)).thenReturn(List.of());

        List<SysMessage> messages = messageService.getMessagesByUserId(toUserId);

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    // ──────────────────────────────────────────────
    // markAsRead()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("markAsRead() should set is_read to 1 and return true")
    void markAsReadSuccess() {
        when(sysMessageMapper.selectById(1L)).thenReturn(sampleMessage);
        when(sysMessageMapper.updateById(any(SysMessage.class))).thenReturn(1);

        boolean result = messageService.markAsRead(1L, toUserId);

        assertTrue(result);
        assertEquals(1, sampleMessage.getIsRead());
        verify(sysMessageMapper).updateById(sampleMessage);
    }

    @Test
    @DisplayName("markAsRead() should reject a message owned by another user")
    void markAsReadRejectsOtherOwner() {
        when(sysMessageMapper.selectById(1L)).thenReturn(sampleMessage);

        boolean result = messageService.markAsRead(1L, 999L);

        assertFalse(result);
        verify(sysMessageMapper, never()).updateById(any(SysMessage.class));
    }

    @Test
    @DisplayName("markAsRead() should return false when message does not exist")
    void markAsReadNotFound() {
        when(sysMessageMapper.selectById(999L)).thenReturn(null);

        boolean result = messageService.markAsRead(999L, toUserId);

        assertFalse(result);
        verify(sysMessageMapper, never()).updateById(any(SysMessage.class));
    }

    // ──────────────────────────────────────────────
    // countUnreadMessages()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("countUnreadMessages() should return the unread count from mapper")
    void countUnreadMessages() {
        when(sysMessageMapper.countUnreadMessages(toUserId)).thenReturn(3);

        int count = messageService.countUnreadMessages(toUserId);

        assertEquals(3, count);
    }

    @Test
    @DisplayName("countUnreadMessages() should return 0 when no unread messages")
    void countUnreadMessagesZero() {
        when(sysMessageMapper.countUnreadMessages(toUserId)).thenReturn(0);

        int count = messageService.countUnreadMessages(toUserId);

        assertEquals(0, count);
    }

    // ──────────────────────────────────────────────
    // markAllAsRead()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("markAllAsRead() should return true when updates are made")
    void markAllAsReadSuccess() {
        when(sysMessageMapper.markAllAsRead(toUserId)).thenReturn(3);

        boolean result = messageService.markAllAsRead(toUserId);

        assertTrue(result);
    }

    @Test
    @DisplayName("markAllAsRead() should return false when no unread messages")
    void markAllAsReadNone() {
        when(sysMessageMapper.markAllAsRead(toUserId)).thenReturn(0);

        boolean result = messageService.markAllAsRead(toUserId);

        assertFalse(result);
    }
}
