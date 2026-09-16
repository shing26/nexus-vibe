package com.nexus.campus.controller;

import com.nexus.campus.dto.ShowcasePostVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The landing page needs a yes/no answer it can trust: either point at a reviewed post
 * or render nothing. An exception or a half-filled object here would put a broken card
 * in front of the one visitor this whole feature exists for.
 */
@ExtendWith(MockitoExtension.class)
class ShowcaseControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Unconfigured means data: null, and no query")
    void returnsNullWhenNotConfigured() {
        var response = new ShowcaseController(jdbcTemplate, "").get();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNull();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("A configured but absent post is still data: null")
    void returnsNullWhenThePostIsNotThere() {
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(List.of());

        var response = new ShowcaseController(jdbcTemplate, "900000000000000001").get();

        assertThat(response.getData()).isNull();
        verify(jdbcTemplate).queryForList(anyString(), anyLong());
    }

    @Test
    @DisplayName("A present, reviewed post is reported with its real score")
    void reportsThePostAndItsScore() {
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(List.of(Map.of(
                "id", 900000000000000001L,
                "title", "Showcase: reviewing an unbounded cache with a stampede window",
                "ai_review_score", 4)));

        ShowcasePostVo vo = new ShowcaseController(jdbcTemplate, "900000000000000001").get().getData();

        assertThat(vo).isNotNull();
        assertThat(vo.getPostId()).isEqualTo(900000000000000001L);
        assertThat(vo.getAiReviewScore()).isEqualTo(4);
        assertThat(vo.getTitle()).contains("unbounded cache");
    }

    @Test
    @DisplayName("A non-numeric configuration is inert rather than fatal")
    void toleratesGarbageConfiguration() {
        var controller = new ShowcaseController(jdbcTemplate, "not-a-number");

        assertThat(controller.get().getData()).isNull();
        verify(jdbcTemplate, never()).queryForList(anyString(), anyLong());
    }
}
