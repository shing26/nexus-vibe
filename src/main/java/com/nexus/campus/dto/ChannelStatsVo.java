package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ChannelStatsVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private String slug;

    private long postCount;
}
