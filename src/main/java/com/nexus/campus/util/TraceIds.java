package com.nexus.campus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One identifier per request, and one per scheduled run, carried in MDC under
 * {@code traceId}.
 *
 * <p>The pipeline crosses threads on purpose: a post is published from the
 * request thread, reviewed on the agent pool, and swept again by a cron task
 * minutes later. Without a shared key those three log fragments are unfusable,
 * which is exactly the gap between "a user says it broke" and "here is what
 * happened". The id is on the log line, on the {@code X-Trace-Id} response
 * header, and on a 5xx body, so all three views name the same run.</p>
 */
public final class TraceIds {

    private static final Logger log = LoggerFactory.getLogger(TraceIds.class);

    /** The MDC key the logback patterns and the JSON encoder render. */
    public static final String MDC_KEY = "traceId";

    /** Response header the id is echoed on, and the only inbound one honoured. */
    public static final String HEADER = "X-Trace-Id";

    /**
     * Inbound ids are only accepted when they look like ids. A caller that can
     * put arbitrary text here puts arbitrary text into every log line of that
     * request, and into a card a user can be asked to read back.
     */
    private static final Pattern INBOUND = Pattern.compile("^[0-9a-zA-Z-]{1,64}$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceIds() {
    }

    /** 16 hex characters: enough to be unique per deployment, short enough to read aloud. */
    public static String newTraceId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Use {@code inbound} when it is a plausible id, otherwise generate a fresh one. */
    public static String orGenerate(String inbound) {
        if (inbound != null && INBOUND.matcher(inbound).matches()) {
            return inbound;
        }
        return newTraceId();
    }

    /**
     * Carry the submitting thread's MDC into a task, then put the worker's own
     * context back so a pooled thread never leaks one request into the next.
     */
    public static Runnable copyMdc(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            applyContext(captured);
            try {
                task.run();
            } finally {
                applyContext(previous);
            }
        };
    }

    /**
     * Run a job body under a trace id. A cron sweep belongs to no request, so it
     * gets a fresh id per execution and the job name is logged with it, because a
     * run that returns early would otherwise leave its id unexplained. When the
     * caller already has an id — the demo controller invokes a sweep from an HTTP
     * request — that id is kept, so the manual run stays part of the request that
     * asked for it.
     */
    public static void runAsJob(String jobName, Runnable body) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        boolean ownsTrace = MDC.get(MDC_KEY) == null;
        if (ownsTrace) {
            String traceId = newTraceId();
            MDC.put(MDC_KEY, traceId);
            log.debug("[TRACE] {} starting as {}", jobName, traceId);
        }
        try {
            body.run();
        } finally {
            applyContext(previous);
        }
    }

    private static void applyContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
