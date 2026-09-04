package com.nexus.campus.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageNotificationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private MessageNotificationService messageNotificationService;

    private final Long userId = 42L;
    private static final String UNREAD_HASH_KEY = "msg:unread";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    // -- incrementUnread() --

    @Test
    @DisplayName("incrementUnread() should use HINCRBY and return the new count")
    void incrementUnreadSuccess() {
        when(hashOperations.increment(UNREAD_HASH_KEY, userId.toString(), 1)).thenReturn(5L);

        long count = messageNotificationService.incrementUnread(userId);

        assertEquals(5L, count);
        verify(hashOperations).increment(UNREAD_HASH_KEY, userId.toString(), 1);
    }

    @Test
    @DisplayName("incrementUnread() should increment multiple times")
    void incrementUnreadMultiple() {
        when(hashOperations.increment(UNREAD_HASH_KEY, userId.toString(), 1))
                .thenReturn(1L, 2L, 3L);

        assertEquals(1L, messageNotificationService.incrementUnread(userId));
        assertEquals(2L, messageNotificationService.incrementUnread(userId));
        assertEquals(3L, messageNotificationService.incrementUnread(userId));
    }

    @Test
    @DisplayName("incrementUnread() should return 0 when Redis returns null")
    void incrementUnreadNull() {
        when(hashOperations.increment(UNREAD_HASH_KEY, userId.toString(), 1)).thenReturn(null);

        long count = messageNotificationService.incrementUnread(userId);

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("incrementUnread() should return 0 when Redis unavailable")
    void incrementUnreadRedisUnavailable() {
        MessageNotificationService service = new MessageNotificationService();

        long count = service.incrementUnread(userId);

        assertEquals(0L, count);
    }

    // -- getUnreadCount() --

    @Test
    @DisplayName("getUnreadCount() should return value from Redis HGET")
    void getUnreadCountSuccess() {
        when(hashOperations.get(UNREAD_HASH_KEY, userId.toString())).thenReturn(3L);

        long count = messageNotificationService.getUnreadCount(userId);

        assertEquals(3L, count);
        verify(hashOperations).get(UNREAD_HASH_KEY, userId.toString());
    }

    @Test
    @DisplayName("getUnreadCount() should return 0 when no entry exists")
    void getUnreadCountNotFound() {
        when(hashOperations.get(UNREAD_HASH_KEY, userId.toString())).thenReturn(null);

        long count = messageNotificationService.getUnreadCount(userId);

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("getUnreadCount() should return 0 when Redis unavailable")
    void getUnreadCountRedisUnavailable() {
        MessageNotificationService service = new MessageNotificationService();

        long count = service.getUnreadCount(userId);

        assertEquals(0L, count);
    }

    // -- clearUnread() --

    @Test
    @DisplayName("clearUnread() should use HDEL to remove the entry")
    void clearUnreadSuccess() {
        messageNotificationService.clearUnread(userId);

        verify(hashOperations).delete(UNREAD_HASH_KEY, userId.toString());
    }

    @Test
    @DisplayName("clearUnread() should handle non-existent entry gracefully")
    void clearUnreadNonExistent() {
        when(hashOperations.delete(UNREAD_HASH_KEY, userId.toString())).thenReturn(0L);

        messageNotificationService.clearUnread(userId);

        verify(hashOperations).delete(UNREAD_HASH_KEY, userId.toString());
    }

    // -- clearUnreadBatch() --

    @Test
    @DisplayName("clearUnreadBatch() should use HDEL to remove entries for multiple users")
    void clearUnreadBatch() {
        List<Long> userIds = Arrays.asList(1L, 2L, 3L);

        messageNotificationService.clearUnreadBatch(userIds);

        verify(hashOperations).delete(UNREAD_HASH_KEY, "1", "2", "3");
    }

    @Test
    @DisplayName("clearUnreadBatch() should handle null or empty list")
    void clearUnreadBatchNull() {
        messageNotificationService.clearUnreadBatch(null);
        messageNotificationService.clearUnreadBatch(List.of());

        verify(hashOperations, never()).delete(anyString(), (Object) any());
    }

    // -- Integration-style: add, get, clear, get again --

    @Test
    @DisplayName("Full workflow: increment, verify count, clear, verify cleared")
    void fullWorkflow() {
        when(hashOperations.increment(UNREAD_HASH_KEY, userId.toString(), 1)).thenReturn(1L);
        when(hashOperations.get(UNREAD_HASH_KEY, userId.toString())).thenReturn(1L, 0L);

        assertEquals(1L, messageNotificationService.incrementUnread(userId));
        assertEquals(1L, messageNotificationService.getUnreadCount(userId));

        messageNotificationService.clearUnread(userId);

        assertEquals(0L, messageNotificationService.getUnreadCount(userId));
    }
}