package com.nexus.campus.service;

import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.dto.PageResult;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.dto.PostVersionVo;
import com.nexus.campus.entity.VibePost;

import java.util.List;

public interface VibePostService {

    VibePost createPost(PostCreateRequest request, Long userId);

    VibePost updatePost(Long postId, PostUpdateRequest request, Long userId);

    VibePost forkPrompt(Long postId, Long userId);

    List<PostVersionVo> getPromptVersions(Long postId);

    boolean restorePromptVersion(Long postId, Integer version, Long userId, String changeNote);

    boolean deletePost(Long postId, Long userId);

    List<PostPageVo> getPendingAuditPosts();

    boolean approvePost(Long postId);

    boolean rejectPost(Long postId);

    PageResult<PostPageVo> getActivePosts(int page, int size);

    PageResult<PostPageVo> getActivePosts(int page, int size, String type);

    PageResult<PostPageVo> getPostsByCategory(Integer categoryId, int page, int size);

    PageResult<PostPageVo> getPostsByCategory(Integer categoryId, int page, int size, String type);

    PageResult<PostPageVo> searchPosts(String keyword, int page, int size);

    PageResult<PostPageVo> filterPosts(int page, int size, String keyword, Integer categoryId,
                                       String language, Integer aiScoreMin, String type, String sort);

    List<PostPageVo> getHotPosts(int limit);

    PostPageVo getPostDetail(Long id);

    VibePost likePost(Long postId);

    boolean incrementView(Long postId);

    PageResult<PostPageVo> getPostsByUserId(Long userId, int page, int size);

    boolean pinPost(Long postId);

    boolean unpinPost(Long postId);
}
