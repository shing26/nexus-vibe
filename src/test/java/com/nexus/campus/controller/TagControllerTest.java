package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.TagUsageVo;
import com.nexus.campus.service.VibeTagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The /tags page reads {@code /tags/usage} and trusts two things from it: the
 * envelope is the standard one, and the order arrives as the backend sorted it
 * (busiest first) — the page deliberately does not re-sort.
 */
@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    @Mock
    private VibeTagService vibeTagService;

    private TagUsageVo usage(int tagId, String name, long postCount) {
        TagUsageVo vo = new TagUsageVo();
        vo.setTagId(tagId);
        vo.setName(name);
        vo.setPostCount(postCount);
        return vo;
    }

    @Test
    @DisplayName("Usage rows pass through with the backend's ordering intact")
    void usagePassesThroughUnresorted() {
        when(vibeTagService.getActiveTagsWithUsage()).thenReturn(List.of(
                usage(3, "prompt-engineering", 12),
                usage(1, "cache", 4)));

        ApiResponse<List<TagUsageVo>> response = new TagController(vibeTagService).getTagUsage();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0).getName()).isEqualTo("prompt-engineering");
        assertThat(response.getData().get(0).getPostCount()).isEqualTo(12);
        assertThat(response.getData().get(1).getPostCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("An empty directory is data, not an error")
    void emptyUsageIsSuccess() {
        when(vibeTagService.getActiveTagsWithUsage()).thenReturn(List.of());

        ApiResponse<List<TagUsageVo>> response = new TagController(vibeTagService).getTagUsage();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEmpty();
    }
}
