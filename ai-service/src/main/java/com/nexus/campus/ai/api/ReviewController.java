package com.nexus.campus.ai.api;

import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import com.nexus.campus.ai.api.dto.SafetyRequest;
import com.nexus.campus.ai.api.dto.SafetyResponse;
import com.nexus.campus.ai.service.PromptFactory;
import com.nexus.campus.ai.service.ReviewService;
import com.nexus.campus.ai.service.SafetyService;
import jakarta.servlet.http.HttpServletResponse;
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

    public static final String PROMPT_VERSION_HEADER = "X-Prompt-Version";

    private final ReviewService reviews;
    private final SafetyService safety;
    private final PromptFactory prompts;

    public ReviewController(ReviewService reviews, SafetyService safety, PromptFactory prompts) {
        this.reviews = reviews;
        this.safety = safety;
        this.prompts = prompts;
    }

    @PostMapping("/reviews")
    public ReviewResponse review(@Valid @RequestBody ReviewRequest request,
                                 HttpServletResponse response) {
        response.setHeader(PROMPT_VERSION_HEADER, prompts.version());
        log.info("review request {} for post {} kind={} trace={} promptVersion={}",
                request.requestId(), request.postId(), request.kind(), traceOf(request.traceId()),
                prompts.version());
        return reviews.review(request);
    }

    @PostMapping("/safety")
    public SafetyResponse classify(@Valid @RequestBody SafetyRequest request,
                                   HttpServletResponse response) {
        response.setHeader(PROMPT_VERSION_HEADER, prompts.version());
        log.info("safety request {} surface={} trace={} promptVersion={}",
                request.requestId(), request.surface(), traceOf(request.traceId()), prompts.version());
        return safety.classify(request);
    }

    private static String traceOf(String traceId) {
        return traceId != null ? traceId : MDC.get("traceId");
    }
}
