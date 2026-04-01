# Stage 1: Build
FROM gradle:8.10-jdk21 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle shadowJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S dashboard && adduser -S dashboard -G dashboard
WORKDIR /app

COPY --from=builder /app/build/libs/token-dashboard-*-all.jar app.jar

RUN mkdir -p /data && chown dashboard:dashboard /data
VOLUME /data

USER dashboard

ENV TD_HOST=0.0.0.0
ENV TD_PORT=8080
ENV TD_OTLP_PORT=4317
ENV TD_DB_PATH=/data/token-dashboard.db

EXPOSE 8080 4317

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
