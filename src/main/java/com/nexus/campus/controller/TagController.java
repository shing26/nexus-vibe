package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.TagUsageVo;
import com.nexus.campus.dto.TagVo;
import com.nexus.campus.entity.VibeTag;
import com.nexus.campus.service.VibeTagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final VibeTagService vibeTagService;

    public TagController(VibeTagService vibeTagService) {
        this.vibeTagService = vibeTagService;
    }

    @GetMapping
    public ApiResponse<List<TagVo>> getTags() {
        return ApiResponse.success(toVo(vibeTagService.getActiveTags()));
    }

    /**
     * The /tags directory's shape: every active tag with how many publicly
     * visible posts carry it, busiest first. A separate endpoint rather than a
     * widened {@link TagVo} so the create-post picker keeps the exact shape it
     * already reads. Cached under the same TTL as the tag list, so a just-created
     * post is counted no sooner than the tag itself appears.
     */
    @GetMapping("/usage")
    public ApiResponse<List<TagUsageVo>> getTagUsage() {
        return ApiResponse.success(vibeTagService.getActiveTagsWithUsage());
    }

    @GetMapping("/post")
    public ApiResponse<List<TagVo>> getTagsByPostId(@RequestParam Long postId) {
        return ApiResponse.success(toVo(vibeTagService.getTagsByPostId(postId)));
    }

    private static List<TagVo> toVo(List<VibeTag> tags) {
        return tags.stream().map(TagVo::from).collect(Collectors.toList());
    }
}
