package com.nexus.campus.service;

import com.nexus.campus.dto.DraftDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    private final ConcurrentHashMap<Long, DraftDto> draftStore = new ConcurrentHashMap<>();

    /**
     * Save draft for a user.
     */
    public void saveDraft(Long userId, DraftDto draft) {
        draft.setLastSaved(LocalDateTime.now());
        draftStore.put(userId, draft);
        log.info("[DraftService] Draft saved for userId={}, title={}", userId, draft.getTitle());
    }

    /**
     * Get draft for a user.
     */
    public DraftDto getDraft(Long userId) {
        DraftDto draft = draftStore.get(userId);
        log.info("[DraftService] Draft retrieved for userId={}, found={}", userId, draft != null);
        return draft;
    }

    /**
     * Delete draft for a user.
     */
    public void deleteDraft(Long userId) {
        DraftDto removed = draftStore.remove(userId);
        log.info("[DraftService] Draft deleted for userId={}, existed={}", userId, removed != null);
    }
}
