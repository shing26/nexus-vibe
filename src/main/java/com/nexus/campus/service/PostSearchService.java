package com.nexus.campus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.campus.dto.PageResult;
import com.nexus.campus.dto.PostPageVo;
import com.nexus.campus.entity.VibePost;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Elasticsearch full-text search service with graceful degradation.
 *
 * <p>Uses the Elasticsearch REST API directly via {@link HttpClient} so that
 * no Spring Boot auto-configuration is needed and the service degrades
 * gracefully when ES is not running.</p>
 */
@Service
public class PostSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostSearchService.class);
    private static final String INDEX_NAME = "nexus_posts";

    @Value("${campus.es.uri:http://localhost:9200}")
    private String esBase;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile boolean esAvailable = false;

    @Autowired
    public PostSearchService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    void init() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                esAvailable = true;
                createIndexIfNotExists();
                log.info("[NEXUS-ES] Elasticsearch connection established at {}", esBase);
            } else {
                log.warn("[NEXUS-ES] Elasticsearch returned status {}, search degraded to MySQL.", resp.statusCode());
            }
        } catch (Exception e) {
            esAvailable = false;
            log.warn("[NEXUS-ES] Elasticsearch not available at {} - search degraded to MySQL. Cause: {}", esBase, e.getMessage());
        }
    }

    /**
     * Ensure the index exists with the CJK mapping this service's queries assume.
     *
     * @return true when the index is present or was created; false when ES could not be asked or
     *         refused the create. Callers that depend on the mapping (rebuildIndex) must not
     *         proceed on false: a bulk into an auto-created index succeeds with the default
     *         analyzer, which indexes Chinese text one character at a time and quietly makes
     *         every multi-character query miss.
     */
    private boolean createIndexIfNotExists() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/" + INDEX_NAME))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                // Index doesn't exist - create it with mapping
                String mapping = "{" +
                        "  \"settings\": {" +
                        "    \"analysis\": {" +
                        "      \"analyzer\": {" +
                        "        \"nexus_analyzer\": {" +
                        "          \"type\": \"cjk\"" +
                        "        }" +
                        "      }" +
                        "    }" +
                        "  }," +
                        "  \"mappings\": {" +
                        "    \"properties\": {" +
                        "      \"id\":         { \"type\": \"long\" }," +
                        "      \"title\":      { \"type\": \"text\", \"analyzer\": \"nexus_analyzer\", \"boost\": 2.0 }," +
                        "      \"content\":    { \"type\": \"text\", \"analyzer\": \"nexus_analyzer\" }," +
                        "      \"summary\":    { \"type\": \"text\" }," +
                        "      \"authorName\": { \"type\": \"keyword\" }," +
                        "      \"categoryName\": { \"type\": \"keyword\" }," +
                        "      \"tags\":       { \"type\": \"keyword\" }," +
                        "      \"createTime\": { \"type\": \"date\", \"format\": \"yyyy-MM-dd HH:mm:ss\" }," +
                        "      \"status\":     { \"type\": \"integer\" }," +
                        "      \"userId\":     { \"type\": \"long\" }," +
                        "      \"viewCount\":  { \"type\": \"integer\" }," +
                        "      \"likeCount\":  { \"type\": \"integer\" }," +
                        "      \"commentCount\": { \"type\": \"integer\" }," +
                        "      \"aiReviewed\":  { \"type\": \"integer\" }," +
                        "      \"aiReviewScore\": { \"type\": \"integer\" }," +
                        "      \"postType\":   { \"type\": \"keyword\" }" +
                        "    }" +
                        "  }" +
                        "}";
                HttpRequest createReq = HttpRequest.newBuilder()
                        .uri(URI.create(esBase + "/" + INDEX_NAME))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(mapping))
                        .build();
                HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
                if (createResp.statusCode() == 200 || createResp.statusCode() == 201) {
                    log.info("[NEXUS-ES] Index '{}' created successfully.", INDEX_NAME);
                    return true;
                } else {
                    log.warn("[NEXUS-ES] Index creation returned {}: {}", createResp.statusCode(), createResp.body());
                    return false;
                }
            } else {
                if (resp.statusCode() == 200) {
                    log.info("[NEXUS-ES] Index '{}' already exists.", INDEX_NAME);
                    return true;
                }
                // Anything else (a 503 from a red cluster, a 401 from a secured one) is "we do not
                // know", which has to be treated as "not ready" rather than as "it exists".
                log.warn("[NEXUS-ES] Index check returned {} - treating the index as not ready.", resp.statusCode());
                return false;
            }
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Failed to check/create index: {}", e.getMessage());
            return false;
        }
    }

    // ================================================
    //  Public API
    // ================================================

    /**
     * Index a single post into Elasticsearch.
     */
    public void indexPost(VibePost post) {
        if (!esAvailable) return;
        try {
            String docJson = buildPostDocument(post);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/" + INDEX_NAME + "/_doc/" + post.getId()))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(docJson))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                log.debug("[NEXUS-ES] Indexed post {}", post.getId());
            } else {
                log.warn("[NEXUS-ES] Index post {} failed: {}", post.getId(), resp.body());
            }
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Failed to index post {}: {}", post.getId(), e.getMessage());
        }
    }

    /**
     * Full-text search across indexed posts.
     *
     * @return PageResult of PostPageVo, or null if ES is unavailable
     */
    @SuppressWarnings("unchecked")
    public PageResult<PostPageVo> searchPosts(String keyword, int page, int size) {
        if (!esAvailable) return null;
        try {
            int from = (page - 1) * size;
            String queryJson = "{" +
                    "  \"from\": " + from + "," +
                    "  \"size\": " + size + "," +
                    "  \"query\": {" +
                    "    \"bool\": {" +
                    "      \"must\": {" +
                    "        \"multi_match\": {" +
                    "          \"query\": \"" + escapeJson(keyword) + "\"," +
                    "          \"fields\": [\"title^2\", \"content\", \"summary\", \"authorName\"]," +
                    "          \"type\": \"best_fields\"" +
                    "        }" +
                    "      }," +
                    "      \"filter\": {" +
                    "        \"term\": { \"status\": 1 }" +
                    "      }" +
                    "    }" +
                    "  }," +
                    "  \"sort\": [ { \"createTime\": \"desc\" } ]" +
                    "}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/" + INDEX_NAME + "/_search"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(queryJson))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("[NEXUS-ES] Search failed: {}", resp.body());
                return null;
            }

            // Parse response
            Map<String, Object> root = objectMapper.readValue(resp.body(), Map.class);
            Map<String, Object> hits = (Map<String, Object>) root.get("hits");
            if (hits == null) return PageResult.of(page, size, 0, List.of());

            int total = Integer.parseInt(
                    ((Map<String, Object>) hits.get("total")).get("value").toString());
            List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");

            List<PostPageVo> results = new ArrayList<>();
            if (hitList != null) {
                for (Map<String, Object> hit : hitList) {
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    if (source != null) {
                        results.add(mapToPostPageVo(source));
                    }
                }
            }

            return PageResult.of(page, size, total, results);
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Search failed, falling back to MySQL: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Delete a post from the index.
     */
    public void deletePost(Long postId) {
        if (!esAvailable) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/" + INDEX_NAME + "/_doc/" + postId))
                    .timeout(Duration.ofSeconds(3))
                    .DELETE()
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Failed to delete post {} from index: {}", postId, e.getMessage());
        }
    }

    /**
     * What a bulk index actually achieved, per Elasticsearch rather than per request.
     *
     * <p>{@code _bulk} answers HTTP 200 while individual items inside the body failed, so the
     * status code cannot be the report. Every count here comes from the item array.</p>
     *
     * @param submitted documents handed to the call
     * @param indexed   documents Elasticsearch confirmed with a 2xx item status
     * @param failed    the difference, including the case where the response could not be read
     */
    public record BulkResult(int submitted, int indexed, int failed) {

        static BulkResult nothingToIndex() {
            return new BulkResult(0, 0, 0);
        }

        /** Everything asked for, nothing confirmed - the ES-unavailable and transport-failure shape. */
        static BulkResult refused(int submitted) {
            return new BulkResult(submitted, 0, submitted);
        }

        /** True only when every submitted document came back accepted. */
        public boolean complete() {
            return failed == 0 && indexed == submitted;
        }
    }

    /**
     * Bulk index a list of posts and report what Elasticsearch accepted.
     *
     * <p>Asks for {@code refresh=true} so a reindex is searchably finished when this returns;
     * without it a caller that reindexes and immediately searches sees nothing.</p>
     */
    public BulkResult bulkIndex(List<VibePost> posts) {
        int submitted = posts == null ? 0 : posts.size();
        if (submitted == 0) {
            return BulkResult.nothingToIndex();
        }
        if (!esAvailable) {
            return BulkResult.refused(submitted);
        }
        try {
            StringBuilder bulkBody = new StringBuilder();
            for (VibePost post : posts) {
                bulkBody.append("{\"index\":{\"_index\":\"").append(INDEX_NAME)
                        .append("\",\"_id\":").append(post.getId()).append("}}\n");
                bulkBody.append(buildPostDocument(post)).append("\n");
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/_bulk?refresh=true"))
                    // A whole-site reindex is one request; the 10s this used to allow is a timeout
                    // on the largest thing the endpoint is for.
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(bulkBody.toString()))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                int indexed = countAcceptedItems(resp.body(), submitted);
                int failed = submitted - indexed;
                if (failed == 0) {
                    log.info("[NEXUS-ES] Bulk indexed {}/{} posts", indexed, submitted);
                } else {
                    log.warn("[NEXUS-ES] Bulk index accepted {} of {} posts; {} item(s) failed",
                            indexed, submitted, failed);
                }
                return new BulkResult(submitted, indexed, failed);
            }
            log.warn("[NEXUS-ES] Bulk index failed: {}", resp.body());
            return BulkResult.refused(submitted);
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Bulk index failed: {}", e.getMessage());
            return BulkResult.refused(submitted);
        }
    }

    /**
     * Count the 2xx items in a {@code _bulk} response body.
     *
     * <p>Fails closed: an unparseable or missing {@code items} array yields zero, because the
     * alternative is reporting an index nobody verified.</p>
     */
    int countAcceptedItems(String responseBody, int submitted) {
        try {
            JsonNode items = objectMapper.readTree(responseBody).path("items");
            if (!items.isArray()) {
                log.warn("[NEXUS-ES] _bulk response carried no items array - counting 0 indexed");
                return 0;
            }
            int accepted = 0;
            for (JsonNode item : items) {
                Iterator<JsonNode> operations = item.elements();
                if (!operations.hasNext()) {
                    continue;
                }
                int status = operations.next().path("status").asInt(0);
                if (status >= 200 && status < 300) {
                    accepted++;
                }
            }
            return Math.min(accepted, submitted);
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Could not parse the _bulk response ({}), counting 0 indexed", e.getMessage());
            return 0;
        }
    }

    /**
     * Recreate the index and bulk-index every provided post.
     *
     * @return what actually landed in a rebuilt index. This used to return the size of the list it
     *         was handed, which is the same number whether Elasticsearch indexed every post or
     *         rejected all of them - the reason a restore could report a warm index over an
     *         empty one.
     */
    public BulkResult rebuildIndex(List<VibePost> posts) {
        int submitted = posts == null ? 0 : posts.size();
        if (!esAvailable) {
            return BulkResult.refused(submitted);
        }
        deleteIndex();
        if (!createIndexIfNotExists()) {
            return BulkResult.refused(submitted);
        }
        return bulkIndex(posts);
    }

    private void deleteIndex() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/" + INDEX_NAME))
                    .timeout(Duration.ofSeconds(5))
                    .DELETE()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 404) {
                log.info("[NEXUS-ES] Index '{}' deleted (or absent).", INDEX_NAME);
            } else {
                log.warn("[NEXUS-ES] Delete index returned {}: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Failed to delete index: {}", e.getMessage());
        }
    }

    public boolean isAvailable() {
        return esAvailable;
    }

    // ================================================
    //  Internal helpers
    // ================================================

    private String buildPostDocument(VibePost post) {
        try {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", post.getId());
            doc.put("title", post.getTitle());
            doc.put("content", post.getContent());
            doc.put("summary", post.getSummary());
            doc.put("authorName", post.getAuthorName() != null ? post.getAuthorName() : "");
            doc.put("categoryName", post.getCategoryName() != null ? post.getCategoryName() : "");
            doc.put("tags", List.of());
            // ES mapping declares format yyyy-MM-dd HH:mm:ss; LocalDateTime.toString()
            // carries sub-second precision (MySQL 6-digit micros), which ES rejects.
            // Truncate to seconds for both indexing and display consistency.
            doc.put("createTime", post.getCreateTime() != null
                    ? post.getCreateTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : "");
            doc.put("status", post.getStatus() != null ? post.getStatus() : 1);
            // Stats and AI fields ride in the index so search results carry the
            // same numbers the DB-path listings show (they were previously null).
            doc.put("userId", post.getUserId());
            doc.put("viewCount", post.getViewCount() != null ? post.getViewCount() : 0);
            doc.put("likeCount", post.getLikeCount() != null ? post.getLikeCount() : 0);
            doc.put("commentCount", post.getCommentCount() != null ? post.getCommentCount() : 0);
            doc.put("aiReviewed", post.getAiReviewed() != null ? post.getAiReviewed() : 0);
            doc.put("aiReviewScore", post.getAiReviewScore());
            doc.put("postType", post.getPostType() != null ? post.getPostType() : "post");
            return objectMapper.writeValueAsString(doc);
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Failed to serialize post {}: {}", post.getId(), e.getMessage());
            return "{}";
        }
    }

    private PostPageVo mapToPostPageVo(Map<String, Object> source) {
        PostPageVo vo = new PostPageVo();
        if (source.get("id") != null) vo.setId(((Number) source.get("id")).longValue());
        if (source.get("title") != null) vo.setTitle((String) source.get("title"));
        if (source.get("content") != null) vo.setContent((String) source.get("content"));
        if (source.get("summary") != null) vo.setSummary((String) source.get("summary"));
        if (source.get("authorName") != null) vo.setAuthorName((String) source.get("authorName"));
        if (source.get("categoryName") != null) vo.setCategoryName((String) source.get("categoryName"));
        if (source.get("createTime") != null) {
            String timeStr = (String) source.get("createTime");
            try {
                vo.setCreateTime(java.time.LocalDateTime.parse(timeStr.replace(" ", "T")));
            } catch (Exception ignored) {}
        }
        // Tags
        if (source.get("tags") instanceof List) {
            List<?> tagList = (List<?>) source.get("tags");
            vo.setTags(tagList.stream().map(Object::toString).toArray(String[]::new));
        }
        // Stats / AI fields: default to 0 rather than null so the search page
        // never renders blanks where the DB-path listings show numbers.
        vo.setStatus(source.get("status") instanceof Number n ? n.intValue() : 1);
        if (source.get("userId") instanceof Number n) vo.setUserId(n.longValue());
        vo.setViewCount(source.get("viewCount") instanceof Number n ? n.intValue() : 0);
        vo.setLikeCount(source.get("likeCount") instanceof Number n ? n.intValue() : 0);
        vo.setCommentCount(source.get("commentCount") instanceof Number n ? n.intValue() : 0);
        vo.setAiReviewed(source.get("aiReviewed") instanceof Number n ? n.intValue() : 0);
        if (source.get("aiReviewScore") instanceof Number n) vo.setAiReviewScore(n.intValue());
        if (source.get("postType") instanceof String s && !s.isBlank()) vo.setPostType(s);
        return vo;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
