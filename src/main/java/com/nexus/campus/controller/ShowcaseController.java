package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.ShowcasePostVo;
import com.nexus.campus.util.ShowcasePostId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the landing page whether there is an already-reviewed post worth pointing at.
 *
 * <p>Unauthenticated on purpose: the whole point is the visitor who has not signed in. It
 * exposes a post id, its title and its score — all of which the public post page already
 * serves — and nothing else. When {@code campus.showcase.post-id} is unset the answer is
 * {@code data: null}, and the landing page renders nothing.</p>
 */
@RestController
@RequestMapping("/api/v1/showcase")
public class ShowcaseController {

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final long postId;

    public ShowcaseController(JdbcTemplate jdbcTemplate,
                              @Value("${campus.showcase.post-id:}") String showcasePostId) {
        this.jdbcTemplate = jdbcTemplate;
        Long configured = ShowcasePostId.parse(showcasePostId);
        this.enabled = configured != null;
        this.postId = configured == null ? 0L : configured;
    }

    @GetMapping
    public ApiResponse<ShowcasePostVo> get() {
        if (!enabled) {
            return ApiResponse.success(null);
        }
        var rows = jdbcTemplate.queryForList("""
                SELECT id, title, ai_review_score
                FROM vibe_post
                WHERE id = ? AND status = 1 AND ai_reviewed = 1
                """, postId);
        if (rows.isEmpty()) {
            return ApiResponse.success(null);
        }
        var row = rows.get(0);
        ShowcasePostVo vo = new ShowcasePostVo();
        vo.setPostId(((Number) row.get("id")).longValue());
        vo.setTitle(String.valueOf(row.get("title")));
        vo.setAiReviewScore(((Number) row.get("ai_review_score")).intValue());
        return ApiResponse.success(vo);
    }
}
