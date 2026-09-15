package com.nexus.campus.config.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis health as the application actually uses it.
 *
 * <p>Two reasons not to keep Boot's {@code RedisHealthIndicator}. It reports DOWN, which the Docker
 * healthcheck turns into a container restart even though posting, reading and logging in all still
 * work without Redis (ADR-0007); and it is registered unconditionally, so the dev profile, where
 * {@code campus.redis.enabled=false} means there is no Redis to be missing, would fail its own
 * health endpoint. Hence the starter indicator is switched off in configuration and this one takes
 * the {@code redis} component name.</p>
 *
 * <p>{@link ObjectProvider} rather than a plain injection: the template bean is declared in a
 * {@code @ConditionalOnProperty} config, so it may be absent. The gate for "does this deployment
 * use Redis" is still the {@code campus.redis.enabled} property, because Boot's own Redis
 * auto-configuration hands out a {@code StringRedisTemplate} as soon as the starter is on the
 * classpath: bean presence says nothing about intent, and without the gate a dev profile spends its
 * health checks pinging a server it never asked for.</p>
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final boolean redisEnabled;

    public RedisHealthIndicator(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                @Value("${campus.redis.enabled:false}") boolean redisEnabled) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.redisEnabled = redisEnabled;
    }

    @Override
    public Health health() {
        if (!redisEnabled) {
            return Health.up().withDetail("mode", "disabled").build();
        }
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            return DependencyHealth.degraded("Redis is enabled but no template bean exists");
        }
        try {
            String pong = template.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up().build();
            }
            return DependencyHealth.degraded("Redis ping returned '" + pong + "'");
        } catch (Exception e) {
            return DependencyHealth.degraded("Redis ping failed: " + e.getMessage());
        }
    }
}
