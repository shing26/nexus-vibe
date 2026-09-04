package com.nexus.campus.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class CommentCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "Post ID is required")
    private Long postId;

    @NotBlank(message = "Content is required")
    private String content;

    private Long parentId = 0L;

    private Long targetId = 0L;
}
