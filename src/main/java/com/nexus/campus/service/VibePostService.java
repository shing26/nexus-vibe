package com.nexus.campus.service;

import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.dto.PageResult;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.dto.PostVersionVo;
import com.nexus.campus.entity.VibePost;

import java.util.List;

public interface VibePostService {

    /** Default page the "waiting for an answer" panel asks for. */
    int DEFAULT_UNANSWERED_SIZE = 5;

    /** Hard ceiling, so the panel can never become a second feed. */
    int MAX_UNANSWERED_SIZE = 20;

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

    /**
     * Recent active posts with no visible comments, newest first. {@code size} is
     * clamped to {@code 1..MAX_UNANSWERED_SIZE}; an empty result is a normal answer.
     */
    List<PostPageVo> getUnansweredPosts(int size);

    PostPageVo getPostDetail(Long id);

    boolean incrementView(Long postId);

    /**
     * Same listing, but when {@code ownerView} is true the caller has already proved the
     * requester is {@code userId}, so held rows are returned and a rejected row explains
     * itself. A false flag is the public view: active rows only, no reason.
     */
    PageResult<PostPageVo> getPostsByUserId(Long userId, int page, int size, boolean ownerView);

    /** Throws 404 when the post is absent and 409 when it is not pinnable. */
    void pinPost(Long postId);

    void unpinPost(Long postId);
}
