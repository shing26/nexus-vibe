package com.nexus.campus.config;

import com.nexus.campus.util.TraceIds;
import org.springframework.core.task.TaskDecorator;

/**
 * Carries the submitting request's MDC, and with it the trace id, onto pool
 * threads. Without it the async review of a post is logged under a thread that
 * has no idea which request caused it.
 */
public class MdcCopyingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return TraceIds.copyMdc(runnable);
    }
}
