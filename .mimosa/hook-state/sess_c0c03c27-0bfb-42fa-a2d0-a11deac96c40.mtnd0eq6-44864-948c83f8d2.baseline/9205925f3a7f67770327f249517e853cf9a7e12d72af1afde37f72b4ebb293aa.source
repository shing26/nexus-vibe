package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.entity.VibeTag;
import com.nexus.campus.service.VibeTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    @Autowired
    private VibeTagService vibeTagService;

    @GetMapping
    public ApiResponse<List<VibeTag>> getTags() {
        return ApiResponse.success(vibeTagService.getActiveTags());
    }

    @GetMapping("/post")
    public ApiResponse<List<VibeTag>> getTagsByPostId(@RequestParam Long postId) {
        return ApiResponse.success(vibeTagService.getTagsByPostId(postId));
    }
}
