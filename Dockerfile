# ============================================================
# Nexus-Campus - Multi-stage Docker Build
# ============================================================

# ---- Stage 1: Build ----
# JDK 21 can compile with --release 18; the 18-based Maven image tag no longer exists.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests -q

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre

ARG JAR_FILE=nexus-campus.jar

LABEL maintainer="Nexus-Campus Team" \
      description="Nexus-Vibe AI developer community platform" \
      version="1.0.0"

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# R5: the JVM runs as an unprivileged account. The process binds 8080, writes under
# /app/uploads and /app/logs, and reads nothing that needs root, so running as uid 0
# bought nothing and cost the containment: a compromised app server that can write
# /app can write anything, and the two named volumes are mounted from the host.
# 10001 rather than 1000 because the first uid on most Linux hosts belongs to a real
# local user, and a container should not be able to pass as them on a mounted volume.
RUN groupadd --system --gid 10001 appgroup \
    && useradd --system --uid 10001 --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

COPY --from=builder --chown=appuser:appgroup /build/target/${JAR_FILE} ./${JAR_FILE}

# Created here rather than at runtime so the image's ownership is what a *new* volume
# inherits; an *existing* volume keeps whatever it already had, which is the upgrade
# hazard documented in docs/plans/pre-deployment-checklist.md.
RUN mkdir -p /app/uploads /app/logs \
    && chown -R appuser:appgroup /app

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 CMD curl -fsS http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

USER appuser

# JVM tuning arrives via JAVA_OPTS (set in docker-compose / runtime env):
# heap percentage, GC choice, GC log rotation. Empty by default.
ENV JAVA_OPTS=""

# A log directory the JVM cannot open used to take the whole container down. Not a logback
# quirk: Spring Boot escalates any error status recorded while it configures logback, so
# RollingFileAppender's failed openFile becomes IllegalStateException during
# prepareEnvironment and the JVM exits 1. Measured on 2026-09-16 by the drill's
# non-root-app-and-the-root-owned-volume-upgrade step, which started the app against a
# deliberately root-owned volume: ten restarts in four minutes, /actuator/health never
# answering, and the public site down. That is the exact shape of the upgrade this image
# ships into, because Docker seeds a named volume from the image only while the volume is
# empty - every host that ran this stack before uid 10001 has a root-owned /app/logs.
#
# So the entry point asks the directory, not the logback config, and falls back to a path it
# can write while saying so on stderr. The site keeps serving with durable logging lost and a
# loud warning, which is ADR-0007's rule applied to the log file: a dependency you can do
# without degrades, it does not kill.
ENTRYPOINT ["sh", "-c", "want=${LOG_DIR:-/app/logs}; if touch \"$want/.writability-probe\" 2>/dev/null; then rm -f \"$want/.writability-probe\"; else rm -f \"$want/.writability-probe\" 2>/dev/null; echo \"WARN: $want is not writable by uid $(id -u); JSON file logging falls back to /tmp/nexus-logs - a host with pre-existing volumes needs the one-time chown in docs/plans/pre-deployment-checklist.md\" >&2; want=/tmp/nexus-logs; mkdir -p \"$want\"; fi; export LOG_DIR=\"$want\"; exec java $JAVA_OPTS -jar nexus-campus.jar"]
