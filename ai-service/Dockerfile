# The application image, built the same way as the root Dockerfile: Maven builder,
# JRE runtime, non-root user. Local builds may fail on the package mirrors; CI is the
# authority for this file.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -g 10001 ai \
    && useradd -u 10001 -g ai -s /usr/sbin/nologin ai

COPY --from=builder /build/target/nexus-ai-service-1.0.0.jar /app/app.jar

USER 10001
EXPOSE 8000

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
