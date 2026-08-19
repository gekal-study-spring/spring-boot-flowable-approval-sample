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
RUN --mount=type=cache,target=/root/.gradle ./gradlew migration:shadowJar --no-daemon

# -----------------------------------------------------------------------------
# Stage 2: Runtime Image
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-noble

# OSパッケージを最新化して攻撃対象領域を減らす
# (Ubuntu 24.04 LTS(noble)ベース。rolling な無印タグと違い pebble を含まず、
#  OpenSSL 等の OS パッケージCVEは apt upgrade で解消される)
RUN apt-get update && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser && useradd -r -g appuser appuser \
    && mkdir -p /app && chown -R appuser:appuser /app

WORKDIR /app

COPY --from=builder /app/migration/build/libs/*.jar migration.jar
COPY --from=builder /app/migration/config/flyway.toml config/flyway.toml

USER appuser

ENV JAVA_OPTS=""
ENV DATASOURCE_URL=""
ENV DATASOURCE_USERNAME=""
ENV DATASOURCE_PASSWORD=""
ENV ENV="dev"

ENV FLYWAY_LOGGERS="slf4j"

# ENV の値を -environment へ渡して flyway.toml の環境別ブロックを切り替える。
# （FLYWAY_ENVIRONMENT 環境変数による指定は Flyway 12.4.0 OSS では機能しない）
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/migration.jar -environment=\"${ENV}\" \"$@\"", "--"]
CMD ["migrate"]
