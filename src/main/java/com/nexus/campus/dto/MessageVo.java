package com.nexus.campus.dto;

import com.nexus.campus.entity.SysMessage;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Response shape for an inbox message; mirrors {@link SysMessage} field for field. */
@Data
public class MessageVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    private Integer type;
    private Integer isRead;
    private LocalDateTime createTime;
    private String fromUserName;
    private String fromUserAvatar;

    public static MessageVo from(SysMessage message) {
        if (message == null) {
            return null;
        }
        MessageVo vo = new MessageVo();
        vo.id = message.getId();
        vo.fromUserId = message.getFromUserId();
        vo.toUserId = message.getToUserId();
        vo.content = message.getContent();
        vo.type = message.getType();
        vo.isRead = message.getIsRead();
        vo.createTime = message.getCreateTime();
        vo.fromUserName = message.getFromUserName();
        vo.fromUserAvatar = message.getFromUserAvatar();
        return vo;
    }
}
