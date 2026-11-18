package com.nexus.campus.controller;

import com.nexus.campus.agent.ChaosTraceService;
import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.ChaosScenarioVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public read surface for the Chaos Theatre. It exposes five precomputed traces
 * and nothing else: no visitor input, no post creation, no model call.
 */
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final ChaosTraceService traceService;

    public DemoController(ChaosTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/chaos-scenarios")
    public ApiResponse<List<ChaosScenarioVo>> chaosScenarios() {
        return ApiResponse.success(traceService.scenarios());
    }
}
