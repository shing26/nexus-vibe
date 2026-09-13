package com.nexus.campus.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The id only helps if it survives the two moves that break it: an async
 * hand-off to a pooled worker, and a cron run that has no request behind it.
 */
class TraceIdsTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Ids are 16 lowercase hex characters and do not repeat")
    void idsAreHexAndUnique() {
        String first = TraceIds.newTraceId();
        String second = TraceIds.newTraceId();

        assertThat(first).matches("^[0-9a-f]{16}$");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("An inbound id is adopted when plausible and replaced when not")
    void inboundIdsAreSanitised() {
        assertThat(TraceIds.orGenerate("a1b2c3d4e5f60718")).isEqualTo("a1b2c3d4e5f60718");
        assertThat(TraceIds.orGenerate("trace-from-upstream-42")).startsWith("trace-from-upstream");
        // A client that can write anything here can write anything into every log
        // line of the request, so anything that is not id-shaped is dropped.
        assertThat(TraceIds.orGenerate("")).hasSize(16);
        assertThat(TraceIds.orGenerate(null)).hasSize(16);
        assertThat(TraceIds.orGenerate("line1\nline2")).hasSize(16);
        assertThat(TraceIds.orGenerate("x".repeat(65))).hasSize(16);
    }

    @Test
    @DisplayName("copyMdc carries the caller's id and restores the worker's own")
    void copyMdcCarriesAndRestores() throws Exception {
        MDC.put(TraceIds.MDC_KEY, "aaaaaaaaaaaaaaaa");
        AtomicReference<String> insideTask = new AtomicReference<>();
        Runnable decorated = TraceIds.copyMdc(() -> insideTask.set(MDC.get(TraceIds.MDC_KEY)));
        // The submitting request thread moves on and clears its own context.
        MDC.clear();

        AtomicReference<String> after = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            MDC.put(TraceIds.MDC_KEY, "worker-leftover");
            decorated.run();
            after.set(MDC.get(TraceIds.MDC_KEY));
        });
        worker.start();
        worker.join(5000);

        // The task ran under the id of the request that queued it...
        assertThat(insideTask.get()).isEqualTo("aaaaaaaaaaaaaaaa");
        // ...and the pooled thread went back to whatever it arrived with.
        assertThat(after.get()).isEqualTo("worker-leftover");
    }

    @Test
    @DisplayName("A job run owns a fresh id, and keeps one it was called with")
    void runAsJobOwnsOrInherits() {
        AtomicReference<String> insideJob = new AtomicReference<>();
        TraceIds.runAsJob("test-job", () -> insideJob.set(TraceIds.current()));

        assertThat(insideJob.get()).matches("^[0-9a-f]{16}$");
        assertThat(TraceIds.current()).isNull();

        MDC.put(TraceIds.MDC_KEY, "bbbbbbbbbbbbbbbb");
        AtomicReference<String> insideCalledFromRequest = new AtomicReference<>();
        TraceIds.runAsJob("test-job", () -> insideCalledFromRequest.set(TraceIds.current()));

        assertThat(insideCalledFromRequest.get()).isEqualTo("bbbbbbbbbbbbbbbb");
        assertThat(TraceIds.current()).isEqualTo("bbbbbbbbbbbbbbbb");
    }
}
