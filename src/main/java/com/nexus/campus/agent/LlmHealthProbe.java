package com.nexus.campus.agent;

/**
 * The one health question the publish gate and reconcile task ask: can the
 * configured inference path answer? Direct mode probes the provider; service
 * mode probes the AI service's {@code /readyz}.
 */
public interface LlmHealthProbe {

    boolean isHealthy();
}
