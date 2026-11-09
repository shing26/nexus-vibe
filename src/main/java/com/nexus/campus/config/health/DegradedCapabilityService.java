package com.nexus.campus.config.health;

import com.nexus.campus.dto.CapabilityStatusVo;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@code deps} health group into the shaped read model the product can show.
 *
 * <p>ADR-0007 split the health surface by audience and deliberately left this fact out of the
 * public one: {@code /actuator/health} answers "should this container be replaced" and
 * {@code /actuator/health/servable} answers "can users still use the site", while the dependency
 * detail lives in {@code /actuator/health/deps} and stays closed at the edge. The degradation
 * banner needs a third question answered - "which optional capability is down, and what does that
 * mean for me" - so this class reads the group internally and emits only that.</p>
 *
 * <p>The vocabulary is closed and is the whole safety property: a row exists here for exactly the
 * optional dependencies whose loss a visitor can notice, each with a fixed key and a fixed
 * sentence. A component that is degraded but absent from {@link #VOCABULARY} (the database, a
 * future indicator) produces nothing, so a new dependency cannot leak its name, status or probe
 * reason through this endpoint by being added to the health group. That is also why the output
 * is built from this table rather than by iterating the health map.</p>
 *
 * <p>{@code db} is deliberately not in the table. It is not an optional capability: the servable
 * group is the database alone, and when the database fails the site is not serving. A degraded
 * database is an operator incident, not a "some features are limited" notice.</p>
 */
@Component
public class DegradedCapabilityService {

    /**
     * Component name in the {@code deps} group -> (key, user-facing consequence).
     *
     * <p>Order is declaration order, so the banner's list is stable between requests. The keys
     * are the client's dismissal unit: changing one is a breaking change for anyone with a
     * dismissed entry in sessionStorage.</p>
     */
    static final List<Capability> VOCABULARY = List.of(
            new Capability("elasticsearch", "search",
                    "Search results may be incomplete."),
            new Capability("llm", "ai-review",
                    "New posts may take longer to appear while automated review is unavailable."),
            new Capability("redis", "cache",
                    "Like counts and trending posts may lag behind."));

    private final HealthEndpoint healthEndpoint;

    public DegradedCapabilityService(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    /** The degraded capabilities, in vocabulary order; empty when everything is up. */
    public List<CapabilityStatusVo> current() {
        HealthComponent deps = healthEndpoint.healthForPath("deps");
        if (deps instanceof CompositeHealth composite) {
            return shape(composite.getComponents());
        }
        // No deps group means no dependency facts to report. Returning nothing is the honest
        // answer: inventing an entry would be worse than the banner staying absent.
        return List.of();
    }

    /**
     * Pure mapping, package-visible so the vocabulary can be pinned without a running context.
     *
     * <p>Only {@link DependencyHealth#DEGRADED} is reported. A component that is DOWN is a
     * different incident - the servable group and the top-level document own it - and a
     * component that is UP has nothing to say.</p>
     */
    List<CapabilityStatusVo> shape(Map<String, HealthComponent> components) {
        List<CapabilityStatusVo> degraded = new ArrayList<>();
        if (components == null) {
            return degraded;
        }
        for (Capability capability : VOCABULARY) {
            HealthComponent component = components.get(capability.component());
            if (component != null && DependencyHealth.DEGRADED.equals(component.getStatus())) {
                degraded.add(new CapabilityStatusVo(capability.key(), capability.impact()));
            }
        }
        return List.copyOf(degraded);
    }

    record Capability(String component, String key, String impact) {
    }
}
