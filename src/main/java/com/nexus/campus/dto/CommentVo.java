package com.nexus.campus.dto;

import com.nexus.campus.entity.VibeComment;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Response shape for a comment. Field names and values mirror
 * {@link VibeComment} exactly — this is a boundary, not a redesign — so that
 * adding a column to the table cannot reach the internet without somebody
 * choosing to add the field here as well.
 */
@Data
public class CommentVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long postId;
    private Long userId;
    private Long parentId;
    private Long targetId;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
    private String authorName;
    private String authorAvatar;

    public static CommentVo from(VibeComment comment) {
        if (comment == null) {
            return null;
        }
        CommentVo vo = new CommentVo();
        vo.id = comment.getId();
        vo.postId = comment.getPostId();
        vo.userId = comment.getUserId();
        vo.parentId = comment.getParentId();
        vo.targetId = comment.getTargetId();
        vo.content = comment.getContent();
        vo.status = comment.getStatus();
        vo.createTime = comment.getCreateTime();
        vo.authorName = comment.getAuthorName();
        vo.authorAvatar = comment.getAuthorAvatar();
        return vo;
    }
}
