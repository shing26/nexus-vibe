package com.nexus.campus.agent;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * A comment summoned the agent with a {@code @ai-agent} mention (ADR-0018).
 * Published after the mentioning comment is committed and consumed on the
 * agent-llm pool.
 *
 * <p>{@code placeholderCommentId} is set only when reconciliation re-runs a
 * mention whose first reply attempt left a visible placeholder: the listener
 * then replaces that comment in place instead of inserting a second reply,
 * so the thread never shows both and never shows neither.</p>
 */
@Getter
public class AgentMentionEvent extends ApplicationEvent {

    private final Long commentId;
    private final Long postId;
    private final String content;
    private final Long authorId;
    private final Long placeholderCommentId;

    public AgentMentionEvent(Object source, Long commentId, Long postId,
                             String content, Long authorId, Long placeholderCommentId) {
        super(source);
        this.commentId = commentId;
        this.postId = postId;
        this.content = content;
        this.authorId = authorId;
        this.placeholderCommentId = placeholderCommentId;
    }
}
