package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Generic pagination result wrapper.
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int page;
    private int size;
    private long total;
    private int pages;
    private List<T> list;

    public PageResult() {
        this.list = Collections.emptyList();
    }

    public PageResult(int page, int size, long total, List<T> list) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.pages = (size > 0) ? (int) Math.ceil((double) total / size) : 0;
        this.list = list != null ? list : Collections.emptyList();
    }

    public static <T> PageResult<T> of(int page, int size, long total, List<T> list) {
        return new PageResult<>(page, size, total, list);
    }
}
