package com.nexus.campus.ai.api;

import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import com.nexus.campus.ai.api.dto.SafetyRequest;
import com.nexus.campus.ai.api.dto.SafetyResponse;
import com.nexus.campus.ai.service.ReviewService;
import com.nexus.campus.ai.service.SafetyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1")
public class ReviewController {

    private final ReviewService reviews;
    private final SafetyService safety;

    public ReviewController(ReviewService reviews, SafetyService safety) {
        this.reviews = reviews;
        this.safety = safety;
    }

    @PostMapping("/reviews")
    public ReviewResponse review(@Valid @RequestBody ReviewRequest request) {
        log.info("review request {} for post {} kind={} trace={}",
                request.requestId(), request.postId(), request.kind(), traceOf(request.traceId()));
        return reviews.review(request);
    }

    @PostMapping("/safety")
    public SafetyResponse classify(@Valid @RequestBody SafetyRequest request) {
        log.info("safety request {} surface={} trace={}",
                request.requestId(), request.surface(), traceOf(request.traceId()));
        return safety.classify(request);
    }

    private static String traceOf(String traceId) {
        return traceId != null ? traceId : MDC.get("traceId");
    }
}
