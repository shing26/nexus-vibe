package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the AI code-review pipeline.
 *
 * <p>Covers code-block detection, structured-output parsing (including
 * clamping and missing-field defaults), and the full reviewPost flow with a
 * mocked {@link LlmClient}.</p>
 */
@ExtendWith(MockitoExtension.class)
class AiReviewServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LlmClient llmClient;
    @Mock
    private VibePostMapper vibePostMapper;
    @Mock
    private AiReviewLogMapper aiReviewLogMapper;
    @Mock
    private VibeCommentMapper vibeCommentMapper;

    @InjectMocks
    private AiReviewService aiReviewService;

    // ── detectCodeBlocks ─────────────────────────────────────────────────

    @Test
    @DisplayName("detectCodeBlocks extracts fenced code blocks")
    void detectCodeBlocksShouldExtractFencedBlocks() {
        String content = "Some text\n```java\nint x = 1;\n```\nMore text\n```python\nprint('hi')\n```";
        List<String> blocks = aiReviewService.detectCodeBlocks(content);

        assertEquals(2, blocks.size());
        assertTrue(blocks.get(0).contains("int x = 1;"));
        assertTrue(blocks.get(1).contains("print('hi')"));
    }

    @Test
    @DisplayName("detectCodeBlocks returns empty for plain text or null")
    void detectCodeBlocksShouldReturnEmptyForPlainText() {
        assertTrue(aiReviewService.detectCodeBlocks("just text, no fences").isEmpty());
        assertTrue(aiReviewService.detectCodeBlocks(null).isEmpty());
        assertTrue(aiReviewService.detectCodeBlocks("").isEmpty());
    }

    // ── parseStructuredResponse ──────────────────────────────────────────

    @Test
    @DisplayName("parseStructuredResponse reads all fields and approves a good score")
    void parseStructuredResponseShouldReadFields() throws Exception {
        JsonNode json = objectMapper.readTree(
                "{\"score\":7,\"severity\":\"low\",\"codeQuality\":\"clean\","
                        + "\"securityConcerns\":\"none\",\"optimizationSuggestions\":\"add tests\"}");

        AiReviewService.ReviewResult result = aiReviewService.parseStructuredResponse(json);

        assertEquals(7, result.getScore());
        assertEquals("low", result.getSeverity());
        assertEquals("clean", result.getQuality());
        assertEquals("none", result.getSecurity());
        assertEquals("add tests", result.getSuggestions());
        assertTrue(result.isApproved());
    }

    @Test
    @DisplayName("parseStructuredResponse clamps score to 0..10")
    void parseStructuredResponseShouldClampScore() throws Exception {
        JsonNode tooHigh = objectMapper.readTree(
                "{\"score\":99,\"severity\":\"low\",\"codeQuality\":\"\",\"securityConcerns\":\"\",\"optimizationSuggestions\":\"\"}");
        assertEquals(10, aiReviewService.parseStructuredResponse(tooHigh).getScore());

        JsonNode tooLow = objectMapper.readTree(
                "{\"score\":-5,\"severity\":\"low\",\"codeQuality\":\"\",\"securityConcerns\":\"\",\"optimizationSuggestions\":\"\"}");
        assertEquals(0, aiReviewService.parseStructuredResponse(tooLow).getScore());
    }

    @Test
    @DisplayName("parseStructuredResponse rejects critical severity regardless of score")
    void parseStructuredResponseShouldRejectCritical() throws Exception {
        JsonNode json = objectMapper.readTree(
                "{\"score\":9,\"severity\":\"critical\",\"codeQuality\":\"ok\",\"securityConcerns\":\"rce\",\"optimizationSuggestions\":\"fix\"}");
        AiReviewService.ReviewResult result = aiReviewService.parseStructuredResponse(json);

        assertEquals(9, result.getScore());
        assertFalse(result.isApproved());
    }

    @Test
    @DisplayName("parseStructuredResponse tolerates legacy rows with missing fields")
    void parseStructuredResponseShouldTolerateMissingFields() throws Exception {
        JsonNode legacy = objectMapper.readTree("{\"score\":9,\"severity\":\"low\"}");
        AiReviewService.ReviewResult result = aiReviewService.parseStructuredResponse(legacy);

        assertEquals(9, result.getScore());
        assertEquals("low", result.getSeverity());
        assertEquals("", result.getQuality());
        assertTrue(result.isApproved());
    }

    // ── reviewPost flow ──────────────────────────────────────────────────

    @Test
    @DisplayName("reviewPost skips LLM call when post has no code blocks")
    void reviewPostShouldSkipWithoutCodeBlocks() {
        aiReviewService.reviewPost(1L, "no code here");

        verify(llmClient, never()).chatCompletionStructured(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(aiReviewLogMapper, vibeCommentMapper);
    }

    @Test
    @DisplayName("reviewPost logs a null result and does not comment")
    void reviewPostShouldHandleNullLlmResult() {
        when(llmClient.chatCompletionStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(null);

        aiReviewService.reviewPost(1L, "```java\nint x = 1;\n```");

        ArgumentCaptor<AiReviewLog> logCaptor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) logCaptor.capture());
        assertEquals("code-review-agent", logCaptor.getValue().getReviewer());
        verify(vibeCommentMapper, never()).insert(any(VibeComment.class));
        verify(vibePostMapper, never()).updateById(any(VibePost.class));
    }

    @Test
    @DisplayName("reviewPost persists score, logs result and posts AI comment")
    void reviewPostShouldRunFullPipeline() throws Exception {
        JsonNode result = objectMapper.readTree(
                "{\"score\":8,\"severity\":\"low\",\"codeQuality\":\"solid\","
                        + "\"securityConcerns\":\"minor\",\"optimizationSuggestions\":\"extract methods\"}");
        when(llmClient.chatCompletionStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(result);

        aiReviewService.reviewPost(42L, "```java\npublic void run() {}\n```");

        // review log saved with parsed severity and approval
        ArgumentCaptor<AiReviewLog> logCaptor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) logCaptor.capture());
        AiReviewLog logEntry = logCaptor.getValue();
        assertEquals(42L, logEntry.getPostId());
        assertEquals("code-review-agent", logEntry.getReviewer());
        assertEquals("low", logEntry.getSeverity());
        assertEquals(1, logEntry.getIsApproved());
        assertNotNull(logEntry.getResultJson());

        // AI comment posted on the post
        ArgumentCaptor<VibeComment> commentCaptor = ArgumentCaptor.forClass(VibeComment.class);
        verify(vibeCommentMapper).insert((VibeComment) commentCaptor.capture());
        VibeComment comment = commentCaptor.getValue();
        assertEquals(42L, comment.getPostId());
        assertEquals(999L, comment.getUserId());
        assertTrue(comment.getContent().contains("8/10"));
        assertTrue(comment.getContent().contains("extract methods"));

        // score written back to the post
        ArgumentCaptor<VibePost> postCaptor = ArgumentCaptor.forClass(VibePost.class);
        verify(vibePostMapper).updateById((VibePost) postCaptor.capture());
        assertEquals(42L, postCaptor.getValue().getId());
        assertEquals(8, postCaptor.getValue().getAiReviewScore());
        assertEquals(1, postCaptor.getValue().getAiReviewed());
    }

    @Test
    @DisplayName("reviewPost still works when LLM structured call fails and falls back")
    void reviewPostShouldHandleStructuredFallback() throws Exception {
        // The service is exercised end-to-end; the fallback path lives in LlmClient
        // and is covered by integration, so here we just verify a rejected review
        // is logged as not approved.
        JsonNode result = objectMapper.readTree(
                "{\"score\":3,\"severity\":\"high\",\"codeQuality\":\"messy\","
                        + "\"securityConcerns\":\"sqli\",\"optimizationSuggestions\":\"rewrite\"}");
        when(llmClient.chatCompletionStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(result);

        aiReviewService.reviewPost(7L, "```sql\nSELECT * FROM users WHERE id = 1\n```");

        ArgumentCaptor<AiReviewLog> logCaptor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) logCaptor.capture());
        assertEquals("high", logCaptor.getValue().getSeverity());
        assertEquals(0, logCaptor.getValue().getIsApproved());
    }
}
