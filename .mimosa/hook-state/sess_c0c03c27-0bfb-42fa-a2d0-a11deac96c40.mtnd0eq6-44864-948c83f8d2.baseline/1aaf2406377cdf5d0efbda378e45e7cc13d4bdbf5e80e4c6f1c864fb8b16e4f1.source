package com.nexus.campus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private void createIndexIfNotExists() {
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
                        "      \"status\":     { \"type\": \"integer\" }" +
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
                } else {
                    log.warn("[NEXUS-ES] Index creation returned {}: {}", createResp.statusCode(), createResp.body());
                }
            } else {
                log.info("[NEXUS-ES] Index '{}' already exists.", INDEX_NAME);
            }
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Failed to check/create index: {}", e.getMessage());
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
     * Bulk index a list of posts.
     */
    public void bulkIndex(List<VibePost> posts) {
        if (!esAvailable || posts == null || posts.isEmpty()) return;
        try {
            StringBuilder bulkBody = new StringBuilder();
            for (VibePost post : posts) {
                bulkBody.append("{\"index\":{\"_index\":\"").append(INDEX_NAME)
                        .append("\",\"_id\":").append(post.getId()).append("}}\n");
                bulkBody.append(buildPostDocument(post)).append("\n");
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esBase + "/_bulk"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(bulkBody.toString()))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.info("[NEXUS-ES] Bulk indexed {} posts", posts.size());
            } else {
                log.warn("[NEXUS-ES] Bulk index failed: {}", resp.body());
            }
        } catch (Exception e) {
            log.warn("[NEXUS-ES] Bulk index failed: {}", e.getMessage());
        }
    }

    /**
     * Recreate the index and bulk-index every provided post.
     *
     * @return number of posts handed to the reindex operation, or 0 when ES is unavailable
     */
    public int rebuildIndex(List<VibePost> posts) {
        if (!esAvailable) return 0;
        deleteIndex();
        createIndexIfNotExists();
        if (posts != null && !posts.isEmpty()) {
            bulkIndex(posts);
        }
        return posts == null ? 0 : posts.size();
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
            doc.put("createTime", post.getCreateTime() != null
                    ? post.getCreateTime().toString().replace("T", " ")
                    : "");
            doc.put("status", post.getStatus() != null ? post.getStatus() : 1);
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
