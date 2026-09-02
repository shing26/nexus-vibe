package com.nexus.campus.agent;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AiReviewEvent extends ApplicationEvent {

    private final Long postId;
    private final String title;
    private final String content;
    private final Long authorId;
    /** true when re-published by the reconciliation task (not the author's first attempt). */
    private final boolean retried;

    public AiReviewEvent(Object source, Long postId, String title, String content, Long authorId) {
        this(source, postId, title, content, authorId, false);
    }

    public AiReviewEvent(Object source, Long postId, String title, String content, Long authorId, boolean retried) {
        super(source);
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.retried = retried;
    }
}
