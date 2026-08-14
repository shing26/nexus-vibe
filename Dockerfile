# ============================================================
# Nexus-Campus - Multi-stage Docker Build
# ============================================================

# ---- Stage 1: Build ----
# JDK 21 can compile with --release 18; the 18-based Maven image tag no longer exists.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml ./src ./

RUN mvn clean package -DskipTests -q

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:18-jre

ARG JAR_FILE=nexus-campus.jar

LABEL maintainer="Nexus-Campus Team" \
      description="AI-Driven Cyberpunk Campus Forum System" \
      version="1.0.0-CYBERPUNK"

WORKDIR /app

COPY --from=builder /build/target/${JAR_FILE} ./${JAR_FILE}

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=mysql
ENV SERVER_PORT=8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 CMD curl -f http://localhost:${SERVER_PORT:-8080}/ || exit 1

ENTRYPOINT ["java", "-jar", "nexus-campus.jar"]
