package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.PageResult;
import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.dto.PostVersionVo;
import com.nexus.campus.entity.Channel;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.service.ChannelService;
import com.nexus.campus.service.VibePostService;
import com.nexus.campus.service.LikeCounterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private VibePostService vibePostService;

    @Autowired
    private LikeCounterService likeCounterService;
 
    @PostMapping("/{id}/pin")
    public ApiResponse<Void> pinPost(@PathVariable Long id,
                                     @RequestAttribute("currentRole") String role) {
        if (!"ADMIN".equals(role)) {
            return ApiResponse.forbidden("Access denied. Admin privileges required.");
        }
        boolean success = vibePostService.pinPost(id);
        if (!success) {
            return ApiResponse.notFound("Post not found or cannot be pinned.");
        }
        return ApiResponse.successMessage("Post pinned.");
    }
 
    @PostMapping("/{id}/unpin")
    public ApiResponse<Void> unpinPost(@PathVariable Long id,
                                       @RequestAttribute("currentRole") String role) {
        if (!"ADMIN".equals(role)) {
            return ApiResponse.forbidden("Access denied. Admin privileges required.");
        }
        boolean success = vibePostService.unpinPost(id);
        if (!success) {
            return ApiResponse.notFound("Post not found.");
        }
        return ApiResponse.successMessage("Post unpinned.");
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createPost(
            @Valid @RequestBody PostCreateRequest request,
            @RequestAttribute("currentUserId") Long userId) {
        VibePost post = vibePostService.createPost(request, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("postId", post.getId().toString());
        data.put("status", post.getStatus());
        if (post.getStatus() == 2) {
            data.put("auditNotice", "Sensitive pattern detected. Shifting to Firewall Queue.");
        }
        return ApiResponse.success("Data injection protocol acknowledged.", data);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request,
            @RequestAttribute("currentUserId") Long userId) {
        VibePost post = vibePostService.updatePost(id, request, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("postId", post.getId().toString());
        return ApiResponse.success("Post updated.", data);
    }

    @PostMapping("/{id}/fork")
    public ApiResponse<Map<String, Object>> forkPrompt(
            @PathVariable Long id,
            @RequestAttribute("currentUserId") Long userId) {
        VibePost fork = vibePostService.forkPrompt(id, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("postId", fork.getId().toString());
        data.put("forkedFromId", id.toString());
        return ApiResponse.success("Template forked.", data);
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<PostVersionVo>> getPromptVersions(@PathVariable Long id) {
        return ApiResponse.success(vibePostService.getPromptVersions(id));
    }

    @PostMapping("/{id}/versions/{version}/restore")
    public ApiResponse<Void> restorePromptVersion(
            @PathVariable Long id,
            @PathVariable Integer version,
            @RequestAttribute("currentUserId") Long userId,
            @RequestBody(required = false) Map<String, String> body) {
        String changeNote = body != null ? body.get("changeNote") : null;
        boolean success = vibePostService.restorePromptVersion(id, version, userId, changeNote);
        if (!success) {
            return ApiResponse.notFound("Version not found.");
        }
        return ApiResponse.successMessage("Version restored.");
    }

    @GetMapping
    public ApiResponse<PageResult<PostPageVo>> getPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String channelSlug,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "false") boolean hot,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer aiScoreMin,
            @RequestParam(required = false) String sort) {
        if (hot) {
            List<PostPageVo> hotPosts = vibePostService.getHotPosts(size);
            return ApiResponse.success(PageResult.of(page, size, hotPosts.size(), hotPosts));
        }
        PageResult<PostPageVo> result;
        Integer resolvedCategoryId = categoryId;
        if (channelSlug != null && !channelSlug.isEmpty()) {
            Channel channel = channelService.getBySlug(channelSlug);
            if (channel == null) {
                return ApiResponse.success(PageResult.of(page, size, 0, Collections.emptyList()));
            }
            resolvedCategoryId = channel.getId();
        }
        boolean hasExtraFilters = (language != null && !language.isEmpty())
                || aiScoreMin != null
                || (sort != null && !sort.isEmpty() && !"latest".equals(sort))
                || (keyword != null && !keyword.isEmpty() && resolvedCategoryId != null);
        if (userId != null) {
            result = vibePostService.getPostsByUserId(userId, page, size);
        } else if (hasExtraFilters) {
            result = vibePostService.filterPosts(page, size, keyword, resolvedCategoryId, language, aiScoreMin, type, sort);
        } else if (keyword != null && !keyword.isEmpty()) {
            result = vibePostService.searchPosts(keyword, page, size);
        } else if (resolvedCategoryId != null) {
            result = vibePostService.getPostsByCategory(resolvedCategoryId, page, size, type);
        } else {
            result = vibePostService.getActivePosts(page, size, type);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/hot")
    public ApiResponse<List<PostPageVo>> getHotPosts(
            @RequestParam(defaultValue = "10") int limit) {
        List<PostPageVo> posts = vibePostService.getHotPosts(limit);
        return ApiResponse.success(posts);
    }

    @GetMapping("/{id}")
    public ApiResponse<PostPageVo> getPostDetail(@PathVariable Long id) {
        vibePostService.incrementView(id);
        PostPageVo post = vibePostService.getPostDetail(id);
        if (post == null) {
            return ApiResponse.notFound("Post not found.");
        }
        return ApiResponse.success(post);
    }

    @PostMapping("/{id}/like")
    public ApiResponse<Map<String, Object>> likePost(
            @PathVariable Long id,
            @RequestAttribute("currentUserId") Long userId) {
        long currentLikes = likeCounterService.likePost(id, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("postId", id.toString());
        data.put("currentLikes", currentLikes);
        return ApiResponse.success("Energy increment synchronized.", data);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long id,
            @RequestAttribute("currentUserId") Long userId) {
        boolean success = vibePostService.deletePost(id, userId);
        if (!success) {
            return ApiResponse.notFound("Post not found.");
        }
        return ApiResponse.successMessage("Post deleted.");
    }
}


