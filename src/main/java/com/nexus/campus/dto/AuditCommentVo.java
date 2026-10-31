package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One comment in the admin audit queue: the held content plus the joined
 * author and post titles needed to judge it. A view, not the entity —
 * entities stop at the service layer (the boundary NoEntityInControllerTest
 * guards).
 */
@Data
public class AuditCommentVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long postId;
    private String postTitle;
    private String authorName;
    private String content;
    private LocalDateTime createTime;
}
