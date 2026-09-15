package com.nexus.campus.dto;

import com.nexus.campus.entity.Channel;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Response shape for a channel; mirrors {@link Channel} field for field. */
@Data
public class ChannelVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;
    private String description;
    private String slug;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;

    public static ChannelVo from(Channel channel) {
        if (channel == null) {
            return null;
        }
        ChannelVo vo = new ChannelVo();
        vo.id = channel.getId();
        vo.name = channel.getName();
        vo.description = channel.getDescription();
        vo.slug = channel.getSlug();
        vo.sortOrder = channel.getSortOrder();
        vo.status = channel.getStatus();
        vo.createTime = channel.getCreateTime();
        return vo;
    }
}
