package com.nexus.campus.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.campus.dto.CrashReportRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The endpoint's whole output is one log line, so the test reads the logback
 * tree directly. The traceId is not asserted here on purpose: it is not part of
 * the message — it arrives through the MDC field the console pattern already
 * prints, set per-request by {@code TraceIdFilter}, exactly like every other
 * request-scoped line.
 */
class CrashReportControllerTest {

    private final CrashReportController controller = new CrashReportController();

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ListAppender<ILoggingEvent> events;
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(CrashReportController.class);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(events);
    }

    @Test
    @DisplayName("A report becomes exactly one WARN line carrying the component stack")
    void oneWarnLinePerReport() {
        CrashReportRequest report = report("boundary", "Cannot read properties of undefined");
        report.setComponentStack("\n    at Sidebar\n    at App");

        var response = controller.report(report);

        assertThat(response.getCode()).isEqualTo(200);
        List<ILoggingEvent> warn = events.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warn).hasSize(1);
        String line = warn.get(0).getFormattedMessage();
        assertThat(line).contains("kind=boundary");
        assertThat(line).contains("at Sidebar");
        assertThat(line).contains("at App");
    }

    @Test
    @DisplayName("A multi-line stack is flattened: the log line cannot be broken into two")
    void stackIsFlattenedToOneLine() {
        CrashReportRequest report = report("window", "boom\nERROR fake second log line");

        controller.report(report);

        String line = events.list.get(0).getFormattedMessage();
        assertThat(line).doesNotContain("\n");
        assertThat(line).doesNotContain("\r");
        assertThat(line).contains("boom");
        assertThat(line).contains("fake second log line");
    }

    @Test
    @DisplayName("Absent fields render as '-', not 'null'")
    void absentFieldsRenderAsDash() {
        CrashReportRequest report = report("promise", "Rejected promise");
        report.setStack(null);
        report.setComponentStack(null);
        report.setUrl(null);

        controller.report(report);

        String line = events.list.get(0).getFormattedMessage();
        assertThat(line).contains("stack=-");
        assertThat(line).contains("componentStack=-");
        assertThat(line).contains("url=-");
    }

    @Test
    @DisplayName("Every field is size-capped: a big body is invalid before the controller runs")
    void fieldsAreSizeCapped() {
        CrashReportRequest report = report("boundary", "m".repeat(501));

        assertThat(validator.validate(report)).isNotEmpty();

        report.setMessage("m");
        report.setStack("s".repeat(4001));
        assertThat(validator.validate(report)).isNotEmpty();

        report.setStack("s".repeat(4000));
        report.setComponentStack("c".repeat(4001));
        assertThat(validator.validate(report)).isNotEmpty();

        report.setComponentStack("c".repeat(4000));
        assertThat(validator.validate(report)).isEmpty();
    }

    @Test
    @DisplayName("The kind field is a closed set, so it cannot smuggle structured content")
    void kindIsAClosedSet() {
        CrashReportRequest report = report("kind=boundary level=ERROR forged line", "m");

        assertThat(validator.validate(report)).isNotEmpty();
    }

    private CrashReportRequest report(String kind, String message) {
        CrashReportRequest report = new CrashReportRequest();
        report.setKind(kind);
        report.setMessage(message);
        report.setUrl("http://localhost:8080/post/1");
        return report;
    }
}
