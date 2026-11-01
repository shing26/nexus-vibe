package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.CrashReportRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives frontend crash reports (FE-1) and turns each one into a single
 * structured log line, tagged with the same traceId as every other request
 * (the TraceIdFilter's MDC is already active here). No storage, no third-party
 * reporter: the deployment already has structured logs, so the report goes
 * where every other diagnostic already lives.
 *
 * <p>The endpoint is deliberately unauthenticated — a crash on the login page
 * is exactly the one worth hearing about. That openness is bounded two ways:
 * the DTO caps every field, and {@link #flatten} strips line breaks and control
 * characters before anything reaches the logger, so a report can pad the log
 * but cannot forge new log lines or fake levels.</p>
 */
@RestController
@RequestMapping("/api/v1/crash-reports")
public class CrashReportController {

    private static final Logger log = LoggerFactory.getLogger(CrashReportController.class);

    /**
     * Single visible marker replacing every newline/tab/control character: the
     * console pattern is line-oriented, so an embedded newline would smuggle a
     * second, attacker-authored line into anything that greps the log.
     */
    private static final String CONTROL_CHARS = "\\p{Cntrl}";

    /**
     * Kept on the plain success envelope (code 200, like every other
     * controller): {@code ApiResponse.success} hardcodes the code, and a
     * {@code @ResponseStatus(ACCEPTED)} body whose envelope said 202 would be
     * exactly the transport/envelope disagreement the envelope was rebuilt to
     * make inexpressible. The browser reporter reads the outcome as "any 2xx
     * means recorded", so nothing else is needed.
     */
    @PostMapping
    public ApiResponse<Void> report(@Valid @RequestBody CrashReportRequest report) {
        log.warn("frontend-crash kind={} url={} message={} stack={} componentStack={}",
                report.getKind(),
                flatten(report.getUrl()),
                flatten(report.getMessage()),
                flatten(report.getStack()),
                flatten(report.getComponentStack()));
        return ApiResponse.success(null);
    }

    private static String flatten(String value) {
        if (value == null) {
            return "-";
        }
        return value.replaceAll(CONTROL_CHARS, "␀");
    }
}
