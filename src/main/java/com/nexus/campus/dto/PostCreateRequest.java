package com.nexus.campus.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

@Data
public class PostCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title must not exceed 150 characters")
    private String title;

    @NotNull(message = "Category is required")
    private Integer categoryId;

    @NotBlank(message = "Content is required")
    private String content;

    private String postType;

    private String promptMetadata;

    private List<Integer> tags;
}
