package com.nexus.campus.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.campus.util.TraceIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hourly hot-ranking sweep belongs to no request, so it has to bring its own
 * trace id. It was the fourth cron and the one the previous round missed, which
 * is exactly the kind of gap that makes "every scheduled run is traceable" a
 * sentence nobody should have to qualify in an incident.
 */
class PostRankingJobTraceTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger traceLogger;

    @AfterEach
    void detach() {
        if (traceLogger != null) {
            traceLogger.detachAppender(appender);
        }
        MDC.clear();
    }

    @Test
    @DisplayName("The hourly sweep runs under a fresh id and leaves no MDC behind")
    void recalculationOpensItsOwnTrace() {
        traceLogger = (Logger) LoggerFactory.getLogger(TraceIds.class);
        appender = new ListAppender<>();
        appender.start();
        traceLogger.addAppender(appender);
        traceLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        // No Redis in a bare instance: the body returns early, which is the point --
        // a run that does nothing still has to be attributable.
        new PostRankingService().recalculateHotRanking();

        assertThat(appender.list)
                .anySatisfy(event -> {
                    assertThat(event.getFormattedMessage()).contains("hot-ranking-recalculate");
                    assertThat(event.getMDCPropertyMap().get(TraceIds.MDC_KEY)).matches("^[0-9a-f]{16}$");
                });
        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }
}
