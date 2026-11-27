package com.nexus.campus.agent;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.enums.AiReviewStatus;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.repository.AiReviewLogRepository;
import com.nexus.campus.repository.VibeCommentRepository;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.service.SysMessageService;
import com.nexus.campus.service.impl.AgentReplyComments;
import com.nexus.campus.task.AiReviewReconcileTask;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Acceptance #4 end to end across the application seams, with no Spring
 * context: the service is down for the first attempt, the post lands FAILED
 * with an {@code unavailable} row and the author notice, then the service
 * recovers and the reconciliation sweep re-publishes the review. The listener
 * claims the next attempt, the service-backed reviewer scores it, and the post
 * reaches REVIEWED with that score.
 *
 * <p>This is the automated equivalent named in the plan; the manual compose
 * sequence remains the demo path, not the evidence path.</p>
 */
class AiServiceModeIntegrationTest {

    private final VibePostMapper vibePostMapper = mock(VibePostMapper.class);
    private final VibeCommentMapper vibeCommentMapper = mock(VibeCommentMapper.class);
    private final AiReviewLogRepository reviewLogs = mock(AiReviewLogRepository.class);
    private final VibePostRepository posts = mock(VibePostRepository.class);
    private final VibeCommentRepository comments = mock(VibeCommentRepository.class);
    private final SysMessageService sysMessages = mock(SysMessageService.class);
    private final AiServiceClient client = mock(AiServiceClient.class);
    private final AgentReplyComments agentReplyComments = mock(AgentReplyComments.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private CampusAiProperties properties;
    private AiReviewService aiReviewService;
    private AiReviewEventListener listener;
    private AiReviewReconcileTask reconcileTask;
    private VibePost stored;

    @BeforeEach
    void setUp() {
        properties = new CampusAiProperties();
        properties.getReview().setEnabled(true);
        properties.getSafety().setEnabled(false);
        properties.getMention().setEnabled(false);
        properties.getReview().setMaxAttempts(5);

        ServiceReviewer reviewer = new ServiceReviewer(client, properties);

        aiReviewService = new AiReviewService();
        ReflectionTestUtils.setField(aiReviewService, "posts", posts);
        ReflectionTestUtils.setField(aiReviewService, "reviewLogs", reviewLogs);
        ReflectionTestUtils.setField(aiReviewService, "comments", comments);
        ReflectionTestUtils.setField(aiReviewService, "sysMessageService", sysMessages);
        ReflectionTestUtils.setField(aiReviewService, "aiProperties", properties);

        listener = new AiReviewEventListener();
        ReflectionTestUtils.setField(listener, "aiReviewService", aiReviewService);
        ReflectionTestUtils.setField(listener, "reviewPolicy", (ReviewPolicy) post -> true);
        ReflectionTestUtils.setField(listener, "reviewers", List.of(reviewer));
        ReflectionTestUtils.setField(listener, "vibePostMapper", vibePostMapper);
        ReflectionTestUtils.setField(listener, "sysMessageService", sysMessages);
        ReflectionTestUtils.setField(listener, "aiProperties", properties);

        reconcileTask = new AiReviewReconcileTask();
        ReflectionTestUtils.setField(reconcileTask, "aiProperties", properties);
        ReflectionTestUtils.setField(reconcileTask, "vibePostMapper", vibePostMapper);
        ReflectionTestUtils.setField(reconcileTask, "vibeCommentMapper", vibeCommentMapper);
        ReflectionTestUtils.setField(reconcileTask, "aiReviewLogMapper",
                mock(com.nexus.campus.agent.AiReviewLogMapper.class));
        ReflectionTestUtils.setField(reconcileTask, "eventPublisher", publisher);
        ReflectionTestUtils.setField(reconcileTask, "llmHealthCache",
                new LlmHealthCache(() -> true));
        ReflectionTestUtils.setField(reconcileTask, "sysMessageService", sysMessages);
        ReflectionTestUtils.setField(reconcileTask, "meterRegistry", new SimpleMeterRegistry());
        ReflectionTestUtils.setField(reconcileTask, "agentReplyComments", agentReplyComments);

        // The "publisher" forwards synchronously, standing in for the async
        // agent pool: the assertion is the state transition, not the threading.
        doAnswer(invocation -> {
            listener.handleAiReviewEvent((AiReviewEvent) invocation.getArgument(0));
            return null;
        }).when(publisher).publishEvent(any());

        stored = post();
        when(vibePostMapper.selectById(42L)).thenReturn(stored);
        when(vibePostMapper.tryClaimReview(eq(42L), any(), any(), anyInt())).thenReturn(1);
        when(vibePostMapper.selectReviewAttempts(42L)).thenReturn(1, 2);
        when(vibePostMapper.selectReviewingBudgetExhausted(anyInt(), anyInt())).thenReturn(List.of());
        when(vibePostMapper.selectStaleAiReviewPosts(eq(AiReviewStatus.REVIEWING.getCode()), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("service down -> FAILED + unavailable; service back -> reconcile completes it as REVIEWED")
    void reconciliationCompletesAFailedServiceReview() {
        when(client.review(any())).thenThrow(new AiServiceClient.AiServiceException(
                "ai_service_unavailable", true, 502, "connection refused"));

        listener.handleAiReviewEvent(new AiReviewEvent(this, 42L, stored.getTitle(),
                stored.getContent(), stored.getUserId(), false));

        verify(reviewLogs).insert(org.mockito.ArgumentMatchers.argThat(
                row -> "unavailable".equals(row.getSeverity())));
        verify(sysMessages).sendMessage(eq(0L), eq(10L),
                org.mockito.ArgumentMatchers.contains("暂时失败"), eq(3));
        verify(posts).update(org.mockito.ArgumentMatchers.argThat(
                update -> update.getAiReviewed() == AiReviewStatus.FAILED.getCode()));

        doReturn(new AiServiceClient.ReviewResponse(
                8, "low",
                "The code is readable and the control flow is easy to follow.",
                "No security concerns were found in this snippet.",
                "Extract the loop body into a helper and add a boundary test.",
                true, "{\"score\":8}", "test-model",
                new AiServiceClient.Usage(100, 20), 10, false, List.of(), 0))
                .when(client).review(any());
        stored.setAiReviewed(AiReviewStatus.FAILED.getCode());
        when(vibePostMapper.selectStaleAiReviewPosts(eq(AiReviewStatus.FAILED.getCode()), any(), anyInt(), anyInt()))
                .thenReturn(List.of(stored));

        reconcileTask.reconcile();

        ArgumentCaptor<VibePost> updates = ArgumentCaptor.forClass(VibePost.class);
        verify(posts, org.mockito.Mockito.atLeastOnce()).update(updates.capture());
        VibePost completed = updates.getAllValues().stream()
                .filter(update -> update.getAiReviewed() == AiReviewStatus.REVIEWED.getCode())
                .findFirst()
                .orElseThrow();
        assertEquals(8, completed.getAiReviewScore());
        verify(reviewLogs).insert(org.mockito.ArgumentMatchers.argThat(
                row -> "low".equals(row.getSeverity()) && row.getIsApproved() == 1));
    }

    @Test
    @DisplayName("the same trace id is sent to the service and logged by the application")
    void traceIdTravelsToTheServiceAndIntoTheJavaLog() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        byte[] body = """
                {"score":8,"severity":"low",
                 "quality":"The code is readable and the control flow is easy to follow.",
                 "security":"No security concerns were found in this snippet.",
                 "suggestions":"Extract the loop body into a helper and add a boundary test.",
                 "approved":true,"resultJson":"{\\"score\\":8}","model":"test-model",
                 "usage":{"promptTokens":100,"completionTokens":20},"latencyMs":10,
                 "repaired":false}
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/reviews", exchange -> {
            received.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        Logger logger = (Logger) LoggerFactory.getLogger(AiReviewService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            properties.getService().setUrl("http://127.0.0.1:" + server.getAddress().getPort());
            AiServiceClient realClient = new AiServiceClient(
                    properties, new SimpleMeterRegistry(), RestClient.builder());
            MDC.put("traceId", "trace-abc");

            aiReviewService.reviewPost(stored, new ServiceReviewer(realClient, properties), 10L, false, 1);

            assertEquals("trace-abc", received.get());
            ILoggingEvent event = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("written back"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("trace-abc", event.getMDCPropertyMap().get("traceId"));
        } finally {
            MDC.clear();
            logger.detachAppender(appender);
            server.stop(0);
        }
    }

    private static VibePost post() {
        VibePost post = new VibePost();
        post.setId(42L);
        post.setTitle("A post");
        post.setPostType("code");
        post.setContent("```java\nint x = 1;\n```");
        post.setUserId(10L);
        post.setAiReviewed(AiReviewStatus.FAILED.getCode());
        return post;
    }
}
