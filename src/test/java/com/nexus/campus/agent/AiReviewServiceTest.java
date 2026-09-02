package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.SysMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the AI code-review pipeline.
 *
 * <p>Covers code-block detection, structured-output parsing (including
 * clamping and missing-field defaults), semantic validation of review
 * results, the one-retry-then-degrade policy for invalid output, and the
 * full reviewPost flow with a mocked {@link LlmClient}.</p>
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
    @Mock
    private SysMessageService sysMessageService;

    @InjectMocks
    private AiReviewService aiReviewService;

    @BeforeEach
    void configureContextBudget() {
        // @Value fields are not populated under Mockito; set the budget explicitly.
        ReflectionTestUtils.setField(aiReviewService, "maxContextTokens", 12000);
    }

    private JsonNode validReview() throws Exception {
        return objectMapper.readTree(
                "{\"score\":8,\"severity\":\"low\","
                        + "\"codeQuality\":\"Clean structure and readable naming throughout the implementation\","
                        + "\"securityConcerns\":\"No SQL injection or unsafe casts detected\","
                        + "\"optimizationSuggestions\":\"Extract the division logic into a helper and add unit tests\"}");
    }

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

    // ── isValidReviewResult ─────────────────────────────────────────────

    @Test
    @DisplayName("isValidReviewResult rejects placeholder uppercase fields and empty suggestions")
    void isValidReviewResultShouldRejectPlaceholders() throws Exception {
        // Regression: a weak local model returned {"codeQuality":"EVALUATE","optimizationSuggestions":""}
        JsonNode garbage = objectMapper.readTree(
                "{\"score\":0,\"severity\":\"low\",\"codeQuality\":\"EVALUATE\","
                        + "\"securityConcerns\":\"LOW\",\"optimizationSuggestions\":\"\"}");
        AiReviewService.ReviewResult result = aiReviewService.parseStructuredResponse(garbage);
        assertFalse(aiReviewService.isValidReviewResult(result));
    }

    @Test
    @DisplayName("isValidReviewResult rejects unknown severity and too-short fields")
    void isValidReviewResultShouldRejectUnknownSeverityAndShortFields() {
        AiReviewService.ReviewResult unknownSeverity = reviewWithSeverity("unknown");
        assertFalse(aiReviewService.isValidReviewResult(unknownSeverity));

        AiReviewService.ReviewResult shortQuality = validResult();
        shortQuality.quality = "Solid code";
        assertFalse(aiReviewService.isValidReviewResult(shortQuality));

        AiReviewService.ReviewResult blankSuggestions = validResult();
        blankSuggestions.suggestions = "   ";
        assertFalse(aiReviewService.isValidReviewResult(blankSuggestions));
    }

    @Test
    @DisplayName("isValidReviewResult accepts substantive prose with a known severity")
    void isValidReviewResultShouldAcceptSubstantiveResult() throws Exception {
        AiReviewService.ReviewResult result = aiReviewService.parseStructuredResponse(validReview());
        assertTrue(aiReviewService.isValidReviewResult(result));
    }

    private AiReviewService.ReviewResult validResult() {
        AiReviewService.ReviewResult result = new AiReviewService.ReviewResult();
        result.score = 7;
        result.severity = "low";
        result.quality = "Clean structure and readable naming throughout the implementation";
        result.security = "No SQL injection or unsafe casts detected";
        result.suggestions = "Extract the division logic into a helper and add unit tests";
        return result;
    }

    private AiReviewService.ReviewResult reviewWithSeverity(String severity) {
        AiReviewService.ReviewResult result = validResult();
        result.severity = severity;
        return result;
    }

    // ── reviewPost flow ──────────────────────────────────────────────────

    @Test
    @DisplayName("reviewPost skips LLM call when post has no code blocks")
    void reviewPostShouldSkipWithoutCodeBlocks() {
        aiReviewService.reviewPost(1L, "Some title", "no code here", 99L, false);

        verify(llmClient, never()).chatCompletionStructured(anyString(), anyString(), anyString(), any(), any());
        verifyNoInteractions(aiReviewLogMapper, vibeCommentMapper);
    }

    @Test
    @DisplayName("reviewPost marks the post FAILED and logs unavailable when LLM is down")
    void reviewPostShouldHandleNullLlmResult() {
        when(llmClient.chatCompletionStructured(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(null);

        aiReviewService.reviewPost(1L, "Some title", "```java\nint x = 1;\n```", 99L, false);

        // one initial call + one retry
        verify(llmClient, times(2)).chatCompletionStructured(anyString(), anyString(), anyString(), any(), any());

        ArgumentCaptor<AiReviewLog> logCaptor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) logCaptor.capture());
        assertEquals("code-review-agent", logCaptor.getValue().getReviewer());
        assertEquals("unavailable", logCaptor.getValue().getSeverity());
        verify(vibeCommentMapper, never()).insert(any(VibeComment.class));
        // author is informed that the review failed and will be retried
        verify(sysMessageService).sendMessage(eq(0L), eq(99L), contains("自动重试"), eq(3));
        ArgumentCaptor<VibePost> postCaptor = ArgumentCaptor.forClass(VibePost.class);
        verify(vibePostMapper).updateById(postCaptor.capture());
        assertEquals(1L, postCaptor.getValue().getId());
        assertEquals(3, postCaptor.getValue().getAiReviewed());
    }

    @Test
    @DisplayName("reviewPost persists score, logs result and posts AI comment")
    void reviewPostShouldRunFullPipeline() throws Exception {
        when(llmClient.chatCompletionStructured(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(validReview());

        aiReviewService.reviewPost(42L, "Calculator", "```java\npublic void run() {}\n```", 42L, false);

        // a single LLM call — valid results are not retried
        verify(llmClient, times(1)).chatCompletionStructured(anyString(), anyString(), anyString(), any(), any());

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
        assertTrue(comment.getContent().contains("add unit tests"));

        // score written back to the post
        ArgumentCaptor<VibePost> postCaptor = ArgumentCaptor.forClass(VibePost.class);
        verify(vibePostMapper).updateById((VibePost) postCaptor.capture());
        assertEquals(42L, postCaptor.getValue().getId());
        assertEquals(8, postCaptor.getValue().getAiReviewScore());
        assertEquals(1, postCaptor.getValue().getAiReviewed());

        // a successful review does not ping the author
        verify(sysMessageService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("reviewPost retries an invalid result with the reinforced prompt, then succeeds")
    void reviewPostShouldRetryInvalidResultOnce() throws Exception {
        JsonNode garbage = objectMapper.readTree(
                "{\"score\":0,\"severity\":\"low\",\"codeQuality\":\"EVALUATE\","
                        + "\"securityConcerns\":\"LOW\",\"optimizationSuggestions\":\"\"}");
        when(llmClient.chatCompletionStructured(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(garbage)
                .thenReturn(validReview());

        aiReviewService.reviewPost(7L, "Calculator", "```java\nint x = 1;\n```", 7L, false);

        // invalid first result → exactly one retry with the reinforced prompt
        verify(llmClient, times(2)).chatCompletionStructured(anyString(), anyString(), anyString(), any(), any());
        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(2)).chatCompletionStructured(
                systemCaptor.capture(), anyString(), anyString(), any(), any());
        assertTrue(systemCaptor.getAllValues().get(1).contains("STRICT OUTPUT REQUIREMENTS"));

        // second result is valid: comment posted, score written back
        verify(vibeCommentMapper).insert(any(VibeComment.class));
        ArgumentCaptor<VibePost> postCaptor = ArgumentCaptor.forClass(VibePost.class);
        verify(vibePostMapper).updateById(postCaptor.capture());
        assertEquals(8, postCaptor.getValue().getAiReviewScore());
    }

    @Test
    @DisplayName("reviewPost degrades silently when both results fail validation")
    void reviewPostShouldDegradeAfterSecondInvalidResult() throws Exception {
        JsonNode garbage = objectMapper.readTree(
                "{\"score\":0,\"severity\":\"low\",\"codeQuality\":\"EVALUATE\","
                        + "\"securityConcerns\":\"LOW\",\"optimizationSuggestions\":\"\"}");
        when(llmClient.chatCompletionStructured(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(garbage);

        aiReviewService.reviewPost(7L, "Calculator", "```java\nint x = 1;\n```", 7L, false);

        // initial call + one retry, then no further LLM work
        verify(llmClient, times(2)).chatCompletionStructured(anyString(), anyString(), anyString(), any(), any());

        // logged as unknown, no score, no comment, post FAILED for reconciliation
        ArgumentCaptor<AiReviewLog> logCaptor = ArgumentCaptor.forClass(AiReviewLog.class);
        verify(aiReviewLogMapper).insert((AiReviewLog) logCaptor.capture());
        assertEquals("unknown", logCaptor.getValue().getSeverity());
        verify(vibeCommentMapper, never()).insert(any(VibeComment.class));
        verify(sysMessageService).sendMessage(eq(0L), eq(7L), contains("自动重试"), eq(3));
        ArgumentCaptor<VibePost> postCaptor = ArgumentCaptor.forClass(VibePost.class);
        verify(vibePostMapper).updateById(postCaptor.capture());
        assertEquals(3, postCaptor.getValue().getAiReviewed());
        assertNull(postCaptor.getValue().getAiReviewScore());
    }
}
