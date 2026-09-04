package com.nexus.campus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis-backed service for unread message notification counting.
 *
 * <p>Uses a Redis Hash ({@code msg:unread}) to track per-user unread counts:
 * <ul>
 *   <li>{@code HINCRBY} to increment when a new message arrives</li>
 *   <li>{@code HDEL} to clear when messages are read</li>
 *   <li>{@code HGET} to retrieve the current unread count</li>
 * </ul>
 */
@Service
public class MessageNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MessageNotificationService.class);

    private static final String UNREAD_HASH_KEY = "msg:unread";

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private boolean redisAvailable;

    /**
     * Increment the unread message count for a given user.
     *
     * @param userId the recipient user ID
     * @return the new unread count after incrementing
     */
    public long incrementUnread(Long userId) {
        if (redisTemplate == null) {
            log.warn("[MSG-NOTIF] Redis unavailable, cannot increment unread count for user {}", userId);
            return 0;
        }
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        Long count = hashOps.increment(UNREAD_HASH_KEY, userId.toString(), 1);
        log.debug("[MSG-NOTIF] Incremented unread for user {} -> {}", userId, count);
        return count != null ? count : 0;
    }

    /**
     * Get the current unread message count for a user.
     *
     * @param userId the user ID to query
     * @return the unread count, or 0 if no entry exists
     */
    public long getUnreadCount(Long userId) {
        if (redisTemplate == null) return 0;
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        Long count = (Long) hashOps.get(UNREAD_HASH_KEY, userId.toString());
        return count != null ? count : 0;
    }

    /**
     * Clear (delete) the unread count entry for a user.
     *
     * @param userId the user whose unread count should be cleared
     */
    public void clearUnread(Long userId) {
        if (redisTemplate == null) return;
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        hashOps.delete(UNREAD_HASH_KEY, userId.toString());
        log.debug("[MSG-NOTIF] Cleared unread count for user {}", userId);
    }

    /**
     * Batch-clear unread counts for multiple users.
     *
     * @param userIds list of user IDs to clear
     */
    public void clearUnreadBatch(List<Long> userIds) {
        if (redisTemplate == null || userIds == null || userIds.isEmpty()) return;
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        hashOps.delete(UNREAD_HASH_KEY, userIds.stream().map(String::valueOf).toArray());
        log.debug("[MSG-NOTIF] Cleared unread for {} users", userIds.size());
    }
}