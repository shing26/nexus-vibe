package com.nexus.campus.ai.api;

import com.nexus.campus.ai.health.ProviderHealthProbe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final ProviderHealthProbe probe;

    public HealthController(ProviderHealthProbe probe) {
        this.probe = probe;
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> readyz() {
        if (probe.isHealthy()) {
            return ResponseEntity.ok(Map.of("status", "ready"));
        }
        return ResponseEntity.status(503)
                .body(Map.of("status", "not_ready", "reason", "provider probe failed"));
    }
}
