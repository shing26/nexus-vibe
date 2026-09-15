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

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar nexus-campus.jar"]
