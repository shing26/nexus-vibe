package com.nexus.campus.repository;

import com.nexus.campus.entity.VibePost;

import java.util.List;
import java.util.Optional;

public interface VibePostRepository {

    Optional<VibePost> findById(Long id);

    Optional<VibePost> findWithDetails(Long id);

    void insert(VibePost post);

    boolean update(VibePost post);

    boolean updateStatus(Long id, int status);

    boolean delete(Long id);

    PageSlice<VibePost> pageActive(int page, int size, Integer categoryId, String postType);

    PageSlice<VibePost> searchPage(int page, int size, String keyword);

    PageSlice<VibePost> filterPage(int page, int size, String keyword, Integer categoryId,
                                   String postType, String language, Integer aiScoreMin, String sort);

    PageSlice<VibePost> pageByUser(int page, int size, Long userId);

    List<VibePost> findPendingAudit();

    boolean incrementView(Long id);

    int recalculateCommentCount(Long postId);

    int pin(Long id);

    int unpin(Long id);
}
