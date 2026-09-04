package com.nexus.campus.dto;

import lombok.Data;

import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

@Data
public class PostUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 150, message = "Title must not exceed 150 characters")
    private String title;
    private Integer categoryId;
    private String content;
    private String postType;
    private String promptMetadata;
    private String changeNote;
    private List<Integer> tags;
}
