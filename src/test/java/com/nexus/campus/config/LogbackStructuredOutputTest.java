package com.nexus.campus.config;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the prod structured-logging contract declared in
 * {@code src/main/resources/logback/prod-file-appender.xml}.
 *
 * <p>Boot's {@code SpringBootJoranConfigurator}, the only parser that understands
 * {@code <springProfile>}, is package-private, so the fragment is parsed with plain logback through
 * a test-only wrapper. The wrapper has to attach {@code ASYNC_FILE} to the root logger because
 * {@code <appender-ref>} elements are not inherited, which in turn rules out a child logger.</p>
 *
 * <p>The wrapper is parsed into the <em>shared</em> {@link LoggerContext}. A private context has no
 * MDC adapter, so rendering a prod line throws inside the appender and the file stays empty, and
 * {@link MDC} only ever writes the shared context anyway. {@code JoranConfigurator#doConfigure}
 * adds to an already configured context instead of replacing it, so
 * {@link #restoreGlobalContext()} detaches and stops exactly what the harness attached and no prod
 * appender leaks into the rest of the fork.</p>
 */
class LogbackStructuredOutputTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROBE_LOGGER = "com.nexus.campus.probe.LogbackProbe";
    private static final String TRACE_ID = "0123456789abcdef";
    private static final String ROOT = Logger.ROOT_LOGGER_NAME;
    private static final String ASYNC_FILE = "ASYNC_FILE";
    private static final String FILE_JSON = "FILE_JSON";

    @TempDir
    Path logDir;

    private LoggerContext loggerContext;
    private Logger rootLogger;
    private Level previousRootLevel;
    private String previousLogDir;

    @BeforeEach
    void parseProdAppenders() throws JoranException {
        previousLogDir = System.getProperty("LOG_DIR");
        System.setProperty("LOG_DIR", logDir.toString());

        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = loggerContext.getLogger(ROOT);
        previousRootLevel = rootLogger.getLevel();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        URL config = getClass().getResource("/logback/prod-appenders-under-test.xml");
        assertThat(config).as("prod appender wrapper on the test classpath").isNotNull();
        configurator.doConfigure(config);
    }

    @AfterEach
    void restoreGlobalContext() {
        drainToFile();
        rootLogger.setLevel(previousRootLevel);
        if (previousLogDir == null) {
            System.clearProperty("LOG_DIR");
        } else {
            System.setProperty("LOG_DIR", previousLogDir);
        }
    }

    /**
     * Detaches and stops the async wrapper, which flushes the nested file appender, so a
     * subsequent read sees every appended line. Idempotent: after a test drained the harness,
     * nothing is attached anymore.
     */
    private void drainToFile() {
        Appender<ILoggingEvent> appender = rootLogger.getAppender(ASYNC_FILE);
        rootLogger.detachAppender(ASYNC_FILE);
        if (appender != null) {
            appender.stop();
        }
    }

    private List<String> rootAppenderNames() {
        List<String> names = new ArrayList<>();
        var iterator = rootLogger.iteratorForAppenders();
        while (iterator.hasNext()) {
            names.add(iterator.next().getName());
        }
        return names;
    }

    @Test
    @DisplayName("prod file logging is a rolling JSON appender behind an async wrapper")
    void prodAppendersAreWiredForStructuredFiles() {
        assertThat(rootAppenderNames()).as("ASYNC_FILE attached to root").contains(ASYNC_FILE);

        AsyncAppender asyncFile = (AsyncAppender) rootLogger.getAppender(ASYNC_FILE);
        assertThat(asyncFile).as("ASYNC_FILE appender").isNotNull();
        assertThat(asyncFile.isStarted()).isTrue();

        @SuppressWarnings("unchecked")
        RollingFileAppender<ILoggingEvent> fileJson =
                (RollingFileAppender<ILoggingEvent>) asyncFile.getAppender(FILE_JSON);
        assertThat(fileJson).as("FILE_JSON appender").isNotNull();
        assertThat(fileJson.getEncoder()).isInstanceOf(LogstashEncoder.class);
        assertThat(normalize(fileJson.getFile()))
                .isEqualTo(normalize(logDir.toString()) + "/nexus-vibe.json");

        RollingPolicy policy = fileJson.getRollingPolicy();
        assertThat(policy).isInstanceOf(SizeAndTimeBasedRollingPolicy.class);
        assertThat(((SizeAndTimeBasedRollingPolicy<?>) policy).getMaxHistory()).isEqualTo(7);
    }

    @Test
    @DisplayName("rendered prod lines are JSON and carry the MDC traceId")
    void renderedLinesAreJsonWithTraceId() throws IOException {
        // Child of root, so it inherits exactly the ASYNC_FILE -> FILE_JSON graph prod uses.
        Logger logger = loggerContext.getLogger(PROBE_LOGGER);

        MDC.put("traceId", TRACE_ID);
        try {
            logger.info("review lease claimed");
            logger.warn("rate limit budget exhausted");
            logger.error("llm call failed", new IllegalStateException("boom"));
        } finally {
            MDC.remove("traceId");
        }
        drainToFile();

        Path jsonFile = logDir.resolve("nexus-vibe.json");
        assertThat(jsonFile).as("JSON log file").exists();
        List<String> lines = Files.readAllLines(jsonFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertThat(lines).as("rendered prod lines").hasSize(3);

        JsonNode infoLine = OBJECT_MAPPER.readTree(lines.get(0));
        assertThat(infoLine.path("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(infoLine.path("message").asText()).isEqualTo("review lease claimed");
        assertThat(infoLine.path("level").asText()).isEqualTo("INFO");
        assertThat(infoLine.path("logger_name").asText()).isEqualTo(PROBE_LOGGER);
        assertThat(infoLine.path("app").asText()).isEqualTo("nexus-vibe");
        assertThat(infoLine.has("@timestamp")).as("@timestamp present").isTrue();

        JsonNode warnLine = OBJECT_MAPPER.readTree(lines.get(1));
        assertThat(warnLine.path("level").asText()).isEqualTo("WARN");
        assertThat(warnLine.path("traceId").asText()).isEqualTo(TRACE_ID);

        JsonNode errorLine = OBJECT_MAPPER.readTree(lines.get(2));
        assertThat(errorLine.path("level").asText()).isEqualTo("ERROR");
        assertThat(errorLine.path("stack_trace").asText()).contains("IllegalStateException: boom");
        assertThat(errorLine.path("traceId").asText()).isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("prod fragment pins the agreed rotation and backpressure caps")
    void prodFragmentDeclaresAgreedCaps() throws IOException {
        // logback 1.5 exposes no getters for maxFileSize / totalSizeCap / queueSize, so the
        // numeric caps are asserted where they are actually configured.
        String fragment;
        try (InputStream in = getClass().getResourceAsStream("/logback/prod-file-appender.xml")) {
            assertThat(in).as("prod appender fragment").isNotNull();
            fragment = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(fragment)
                .contains("${LOG_DIR:-/app/logs}")
                .contains("${LOG_MAX_FILE_SIZE:-100MB}")
                .contains("${LOG_MAX_HISTORY:-7}")
                .contains("${LOG_TOTAL_SIZE_CAP:-1GB}")
                .contains("<queueSize>512</queueSize>")
                .contains("<discardingThreshold>0</discardingThreshold>");
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
