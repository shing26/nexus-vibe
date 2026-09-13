package com.nexus.campus.config.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * The status vocabulary shared by the dependency indicators.
 *
 * <p>Boot ships UP, DOWN, OUT_OF_SERVICE and UNKNOWN; none of them says "this dependency is gone
 * but the site still works", and DOWN is the wrong word for it because DOWN gets the container
 * killed by the Docker healthcheck. {@code DEGRADED} exists so a Redis or Elasticsearch outage can
 * be reported without being fatalized. See ADR-0007.</p>
 */
final class DependencyHealth {

    static final Status DEGRADED = new Status("DEGRADED", "dependency unavailable, still serving");

    private DependencyHealth() {
    }

    static Health degraded(String reason) {
        return Health.status(DEGRADED).withDetail("reason", reason).build();
    }
}
