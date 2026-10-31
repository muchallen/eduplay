# syntax=docker/dockerfile:1.7

# ---------- Build stage: compile and package the executable JAR ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so they are cached between source-only changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline -DskipTests || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/eduplay-backend.jar extract --layers --launcher --destination /build/extracted

# ---------- Runtime stage: slim JRE, non-root ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S -g 10001 eduplay && adduser -S -u 10001 -G eduplay -h /app eduplay
WORKDIR /app

# Copy Spring Boot layers from least to most frequently changing for better image caching.
COPY --from=build --chown=eduplay:eduplay /build/extracted/dependencies/ ./
COPY --from=build --chown=eduplay:eduplay /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=eduplay:eduplay /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=eduplay:eduplay /build/extracted/application/ ./

ENV TZ=UTC \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Djava.security.egd=file:/dev/./urandom -Duser.timezone=UTC -Dfile.encoding=UTF-8"

USER 10001:10001
EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${MANAGEMENT_PORT:-8081}/actuator/health/liveness >/dev/null || exit 1

# exec makes the JVM PID 1 so it receives SIGTERM directly and shuts down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher \"$@\"", "--"]
