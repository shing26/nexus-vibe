package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.config.CampusAiProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * The application-side half of the typed contract. It makes exactly one HTTP
 * call per operation; retries belong to the service and to the lease loop.
 */
@Slf4j
@Component
public class AiServiceClient implements LlmHealthProbe {

    private final RestClient restClient;
    private final RestClient healthClient;
    private final String apiKey;
    private final MeterRegistry meters;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiServiceClient(CampusAiProperties properties,
                           MeterRegistry meters,
                           RestClient.Builder restClientBuilder) {
        CampusAiProperties.Service service = properties.getService();
        this.apiKey = service.getApiKey();
        this.meters = meters;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(service.getConnectTimeout());
        factory.setReadTimeout(service.getReadTimeout());
        this.restClient = restClientBuilder
                .baseUrl(service.getUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .requestFactory(factory)
                .build();

        SimpleClientHttpRequestFactory healthFactory = new SimpleClientHttpRequestFactory();
        healthFactory.setConnectTimeout(Duration.ofSeconds(2));
        healthFactory.setReadTimeout(Duration.ofSeconds(2));
        this.healthClient = RestClient.builder()
                .baseUrl(service.getUrl())
                .defaultHeader("Accept", "application/json")
                .requestFactory(healthFactory)
                .build();
    }

    public ReviewResponse review(ReviewRequest request) {
        return post("/v1/reviews", request, ReviewResponse.class, "review");
    }

    public SafetyResponse safety(SafetyRequest request) {
        return post("/v1/safety", request, SafetyResponse.class, "safety");
    }

    @Override
    public boolean isHealthy() {
        try {
            healthClient.get().uri("/readyz").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("AI service health probe failed: {}", e.getMessage());
            return false;
        }
    }

    private <T> T post(String path, Object request, Class<T> type, String operation) {
        try {
            T response = restClient.post()
                    .uri(path)
                    .headers(headers -> {
                        String traceId = MDC.get("traceId");
                        if (traceId != null && !traceId.isBlank()) {
                            headers.set("X-Trace-Id", traceId);
                        }
                        if (apiKey != null && !apiKey.isBlank()) {
                            headers.set("X-AI-Service-Key", apiKey);
                        }
                    })
                    .body(request)
                    .retrieve()
                    .body(type);
            if (response == null) {
                count(operation, "error");
                throw new AiServiceException("ai_service_invalid_response", false, 502,
                        "AI service returned an empty body for " + path);
            }
            count(operation, "success");
            return response;
        } catch (RestClientResponseException e) {
            count(operation, "error");
            ErrorBody body = errorBody(e);
            throw new AiServiceException(body.code(), body.retryable(),
                    e.getStatusCode().value(), body.message());
        } catch (ResourceAccessException e) {
            count(operation, "error");
            if (hasCause(e, SocketTimeoutException.class)) {
                throw new AiServiceException("ai_service_timeout", true, 504,
                        "AI service did not answer in time: " + e.getMessage());
            }
            throw new AiServiceException("ai_service_unavailable", true, 502,
                    "AI service call failed: " + e.getMessage());
        } catch (RestClientException e) {
            // A 200 whose body does not match the contract is not an
            // availability failure: the service answered. Mark it invalid so
            // the caller records "unknown" through ReviewValidity instead of
            // "unavailable", which would look like a network problem.
            count(operation, "error");
            if (hasCause(e, SocketTimeoutException.class)) {
                throw new AiServiceException("ai_service_timeout", true, 504,
                        "AI service did not answer in time: " + e.getMessage());
            }
            throw new AiServiceException("ai_service_invalid_response", false, 502,
                    "AI service returned an unreadable response: " + e.getMessage());
        } catch (AiServiceException e) {
            // Our own mapping (for example the empty-body guard) must not be
            // re-wrapped as a transport failure.
            throw e;
        } catch (Exception e) {
            count(operation, "error");
            if (hasCause(e, SocketTimeoutException.class)) {
                throw new AiServiceException("ai_service_timeout", true, 504,
                        "AI service did not answer in time: " + e.getMessage());
            }
            throw new AiServiceException("ai_service_unavailable", true, 502,
                    "AI service call failed: " + e.getMessage());
        }
    }

    private ErrorBody errorBody(RestClientResponseException error) {
        String fallbackCode = fallbackCode(error.getStatusCode().value());
        try {
            JsonNode node = objectMapper.readTree(error.getResponseBodyAsString());
            if (node == null || node.isMissingNode() || !node.isObject()) {
                return new ErrorBody(fallbackCode, error.getStatusText(),
                        error.getStatusCode().is5xxServerError());
            }
            return new ErrorBody(
                    node.path("code").asText(fallbackCode),
                    node.path("message").asText(error.getStatusText()),
                    node.path("retryable").asBoolean(error.getStatusCode().is5xxServerError()));
        } catch (Exception ignored) {
            return new ErrorBody(fallbackCode, error.getStatusText(),
                    error.getStatusCode().is5xxServerError());
        }
    }

    /**
     * The JDK HTTP client drops the body of a 401 on some paths, so the status
     * is the only reliable evidence. These codes are the ones the contract
     * assigns to those statuses; the caller's {@code retryable} decision still
     * comes from the status.
     */
    private static String fallbackCode(int status) {
        return switch (status) {
            case 401, 403 -> "unauthorized";
            case 404 -> "not_found";
            case 503 -> "not_ready";
            default -> "ai_service_error";
        };
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void count(String operation, String outcome) {
        Counter.builder("ai.service.requests")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meters)
                .increment();
    }

    public record ReviewRequest(String requestId,
                                String traceId,
                                Long postId,
                                String kind,
                                String title,
                                String content,
                                List<String> snippets,
                                Budget budget) {
    }

    public record Budget(int maxContextTokens, int timeoutMs) {
    }

    public record ReviewResponse(int score,
                                 String severity,
                                 String quality,
                                 String security,
                                 String suggestions,
                                 boolean approved,
                                 String resultJson,
                                 String model,
                                 Usage usage,
                                 long latencyMs,
                                 boolean repaired,
                                 List<Evidence> evidence,
                                 int evidenceRejected) {
    }

    public record Usage(int promptTokens, int completionTokens) {
    }

    public record Evidence(String quote, int startLine, int endLine) {
    }

    public record SafetyRequest(String requestId,
                                String traceId,
                                String text,
                                String surface) {
    }

    public record SafetyResponse(String classification,
                                 double confidence,
                                 List<String> reasons,
                                 String resultJson,
                                 String model,
                                 long latencyMs) {
    }

    private record ErrorBody(String code, String message, boolean retryable) {
    }

    public static class AiServiceException extends RuntimeException {

        private final String code;
        private final boolean retryable;
        private final int status;

        public AiServiceException(String code, boolean retryable, int status, String message) {
            super(message);
            this.code = code;
            this.retryable = retryable;
            this.status = status;
        }

        public String getCode() {
            return code;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public int getStatus() {
            return status;
        }
    }
}
