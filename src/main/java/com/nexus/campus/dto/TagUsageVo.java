package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * A tag with how many publicly visible posts use it — the shape behind the
 * /tags page's count badges. Counts come straight from the
 * {@code selectActiveTagsWithUsage} join, which only tallies posts whose
 * status is ACTIVE, so a post held for moderation never inflates a tag.
 */
@Data
public class TagUsageVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer tagId;
    private String name;
    private Long postCount;
}
