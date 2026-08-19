# syntax=docker/dockerfile:1

# Refercence
# https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html

# -----------------------------------------------------------------------------
# Stage 1: Build the application
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /app

# Gradle wrapper and configuration files
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY app/build.gradle app/
COPY migration/build.gradle migration/
COPY gradle/libs.versions.toml gradle/

# Download dependencies
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null || true

# Copy source code
COPY . .

# Build the application
RUN --mount=type=cache,target=/root/.gradle ./gradlew app:bootJar --no-daemon

# -----------------------------------------------------------------------------
# Stage 2: Extract Layers
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-noble AS extractor
WORKDIR /builder

COPY --from=builder /app/app/build/libs/*.jar application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# -----------------------------------------------------------------------------
# Stage 3: Runtime Image
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-noble

# OSパッケージを最新化して攻撃対象領域を減らす
# (Ubuntu 24.04 LTS(noble)ベース。rolling な無印タグと違い pebble を含まず、
#  OpenSSL 等の OS パッケージCVEは apt upgrade で解消される)
RUN apt-get update && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser && useradd -r -g appuser appuser \
    && mkdir -p /app && chown -R appuser:appuser /app

WORKDIR /app

USER appuser

COPY --from=extractor /builder/extracted/dependencies/ ./
COPY --from=extractor /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor /builder/extracted/application/ ./

ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/application.jar"]
