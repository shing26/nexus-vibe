package com.nexus.campus.agent;

/**
 * Async event carrying a comment through the semantic safety check.
 * <p>
 * Unlike the post path, a comment is already publicly visible when this event
 * fires (ADR-0017): there is no fail-closed hold at publish time, only the
 * verdict-driven handling that follows.
 */
public class AiCommentSafetyCheckEvent {

    private final Object source;
    private final Long commentId;
    private final Long postId;
    private final String content;
    private final Long authorId;

    public AiCommentSafetyCheckEvent(Object source, Long commentId, Long postId,
                                     String content, Long authorId) {
        this.source = source;
        this.commentId = commentId;
        this.postId = postId;
        this.content = content;
        this.authorId = authorId;
    }

    public Object getSource() {
        return source;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Long getPostId() {
        return postId;
    }

    public String getContent() {
        return content;
    }

    public Long getAuthorId() {
        return authorId;
    }
}
