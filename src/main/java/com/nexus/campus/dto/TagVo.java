package com.nexus.campus.dto;

import com.nexus.campus.entity.VibeTag;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Response shape for a tag; mirrors {@link VibeTag} field for field. */
@Data
public class TagVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;
    private Integer status;
    private LocalDateTime createTime;

    public static TagVo from(VibeTag tag) {
        if (tag == null) {
            return null;
        }
        TagVo vo = new TagVo();
        vo.id = tag.getId();
        vo.name = tag.getName();
        vo.status = tag.getStatus();
        vo.createTime = tag.getCreateTime();
        return vo;
    }
}
