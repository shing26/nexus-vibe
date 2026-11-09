package com.nexus.campus.controller;

import com.nexus.campus.config.health.DegradedCapabilityService;
import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.CapabilityStatusVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The product's own answer to "is something down right now", for the banner.
 *
 * <p>Read-only, GET-only and anonymous: the reader this exists for is a visitor who has not
 * signed in, and requiring a token would hide from exactly the people it is meant to reassure.
 * The path sits under {@code /api/}, which is the only prefix nginx proxies, and carries no
 * actuator word, so the edge configuration does not have to open anything new. See ADR-0007
 * for why this is a separate surface rather than a re-pointing of {@code /actuator/health}.</p>
 *
 * <p>Nothing in the response comes straight from the health document. It is shaped by
 * {@link DegradedCapabilityService} into a closed vocabulary of capability keys and sentences.</p>
 */
@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

    private final DegradedCapabilityService degradedCapabilities;

    public StatusController(DegradedCapabilityService degradedCapabilities) {
        this.degradedCapabilities = degradedCapabilities;
    }

    @GetMapping("/capabilities")
    public ApiResponse<List<CapabilityStatusVo>> capabilities() {
        return ApiResponse.success(degradedCapabilities.current());
    }
}
