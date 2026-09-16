package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;

/** The landing-page pointer to one already-reviewed post. Null fields mean "no showcase". */
@Data
public class ShowcasePostVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long postId;
    private String title;
    private Integer aiReviewScore;
}
