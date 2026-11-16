package com.nexus.campus.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.dto.AiReviewDetail;
import com.nexus.campus.dto.UserReviewEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiReviewDetailService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiReviewDetail toDetail(AiReviewLog log) {
        if (log == null) {
            return null;
        }
        JsonNode result = parseResult(log.getResultJson());
        AiReviewDetail detail = new AiReviewDetail();
        detail.setPostId(log.getPostId());
        detail.setReviewer(log.getReviewer());
        detail.setScore(score(result));
        detail.setSeverity(severity(result, log.getSeverity()));
        detail.setIsApproved(isApproved(result, log.getIsApproved()));
        detail.setCodeQuality(textValue(result, "codeQuality"));
        detail.setSecurityConcerns(textValue(result, "securityConcerns"));
        detail.setOptimizationSuggestions(textValue(result, "optimizationSuggestions"));
        detail.setReviewedAt(log.getCreatedAt());
        detail.setDurationMs(durationMs(result));
        return detail;
    }

    /**
     * Project one stored review row onto the user-scoped evidence index. The
     * score/severity/approval/time/duration fields are read exactly as
     * {@link #toDetail} reads them, so a profile average computed from these
     * entries matches {@code UserProfileSummaryService}'s average over the
     * same rows.
     */
    public UserReviewEntry toUserReviewEntry(AiReviewLog log, String postTitle) {
        if (log == null) {
            return null;
        }
        JsonNode result = parseResult(log.getResultJson());
        UserReviewEntry entry = new UserReviewEntry();
        entry.setLogId(log.getId());
        entry.setPostId(log.getPostId());
        entry.setPostTitle(postTitle);
        entry.setScore(score(result));
        entry.setSeverity(severity(result, log.getSeverity()));
        entry.setIsApproved(isApproved(result, log.getIsApproved()));
        entry.setReviewedAt(log.getCreatedAt());
        entry.setDurationMs(durationMs(result));
        return entry;
    }

    public Integer score(AiReviewLog log) {
        if (log == null) {
            return null;
        }
        return score(parseResult(log.getResultJson()));
    }

    private JsonNode parseResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(resultJson);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Integer score(JsonNode result) {
        if (result == null) {
            return null;
        }
        JsonNode scoreNode = result.get("score");
        if (scoreNode == null || scoreNode.isNull() || !scoreNode.isNumber()) {
            return null;
        }
        return Math.max(0, Math.min(10, scoreNode.asInt()));
    }

    /**
     * Optional timing instrument written by AiReviewService. Absent means "this
     * row predates the instrument" (or the attempt had no payload), not zero.
     */
    private Integer durationMs(JsonNode result) {
        if (result == null) {
            return null;
        }
        JsonNode node = result.get("durationMs");
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return Math.max(0, node.asInt());
    }

    private String severity(JsonNode result, String fallback) {
        String severity = textValue(result, "severity");
        if (severity.isBlank() && fallback != null && !fallback.isBlank()) {
            severity = fallback;
        }
        return severity.isBlank() ? "unknown" : severity.trim().toLowerCase();
    }

    private Boolean isApproved(JsonNode result, Integer columnApproved) {
        if (result != null) {
            Boolean isApproved = booleanValue(result.get("isApproved"));
            if (isApproved == null) {
                isApproved = booleanValue(result.get("approved"));
            }
            if (isApproved == null) {
                isApproved = booleanValue(result.get("verdict"));
            }
            if (isApproved != null) {
                return isApproved;
            }
        }
        if (columnApproved != null) {
            return columnApproved == 1;
        }
        return null;
    }

    private Boolean booleanValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.asInt() != 0;
        }
        if (node.isTextual()) {
            String text = node.asText().trim().toLowerCase();
            if ("true".equals(text) || "1".equals(text) || "yes".equals(text)
                    || "approved".equals(text) || "approve".equals(text)) {
                return true;
            }
            if ("false".equals(text) || "0".equals(text) || "no".equals(text)
                    || "needs attention".equals(text) || "needs review".equals(text)
                    || "rejected".equals(text) || "reject".equals(text)) {
                return false;
            }
        }
        return null;
    }

    private String textValue(JsonNode result, String field) {
        if (result == null) {
            return "";
        }
        JsonNode node = result.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                if (!item.isNull()) {
                    values.add(item.asText());
                }
            });
            return String.join("\n", values);
        }
        return node.asText();
    }
}
