package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.TagVo;
import com.nexus.campus.entity.VibeTag;
import com.nexus.campus.service.VibeTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    @Autowired
    private VibeTagService vibeTagService;

    @GetMapping
    public ApiResponse<List<TagVo>> getTags() {
        return ApiResponse.success(toVo(vibeTagService.getActiveTags()));
    }

    @GetMapping("/post")
    public ApiResponse<List<TagVo>> getTagsByPostId(@RequestParam Long postId) {
        return ApiResponse.success(toVo(vibeTagService.getTagsByPostId(postId)));
    }

    private static List<TagVo> toVo(List<VibeTag> tags) {
        return tags.stream().map(TagVo::from).collect(Collectors.toList());
    }
}
