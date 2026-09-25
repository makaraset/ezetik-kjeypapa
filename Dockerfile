# Kjey PAPA backend — container image for SBF's API host (10.32.8.99).
#
# Two stages so the runtime image carries a JRE and a jar, not a JDK, Maven and
# a source tree. Build:
#
#   docker build -t kjeypapa-api:0.0.1 .
#
# Nothing secret is baked in. Every credential arrives as an environment
# variable or a mounted file at run time — see docker-compose.yml.

# ---------------------------------------------------------------------------
# Stage 1 — build
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so a source-only change does not
# re-download the world. This is the difference between a 30-second rebuild and
# a five-minute one on a 2-CPU box.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Tests are skipped deliberately: they belong in CI against a database, not in
# an image build that has none. Run them before you build, not during.
RUN mvn -B -q clean package -DskipTests \
 && mv target/ezetik-kjeypapa-*.jar /build/app.jar

# ---------------------------------------------------------------------------
# Stage 2 — runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

# curl is for the HEALTHCHECK below. The JRE image does not guarantee it, and a
# healthcheck whose binary is missing marks the container unhealthy forever.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# The host runs UTC. Every scheduler and the consent PDF pin Asia/Phnom_Penh
# explicitly, so they are unaffected either way — but NoteServiceImpl formats
# dates in the JVM default zone, and a Cambodian customer should not read a UTC
# date. Set here so that one place is right; nothing else depends on it.
ENV TZ=Asia/Phnom_Penh
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Never run a public-facing service as root.
RUN groupadd --system --gid 1001 kjeypapa \
 && useradd --system --uid 1001 --gid kjeypapa --create-home kjeypapa

WORKDIR /app
COPY --from=build --chown=kjeypapa:kjeypapa /build/app.jar /app/app.jar

# Uploads are written at runtime; keep them on a volume, not in the image layer.
RUN mkdir -p /app/uploads && chown kjeypapa:kjeypapa /app/uploads
VOLUME ["/app/uploads"]

USER kjeypapa
EXPOSE 8080

# 4 GB host, and the JVM is not the only thing on it. MaxRAMPercentage is
# container-aware; a fixed -Xmx would be wrong the moment the box is resized.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Hits a whitelisted endpoint that reads the dictionary, so a pass means the
# application is up AND the database is answering. /actuator/health is not on
# the security whitelist and would report 403 as "unhealthy".
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:8080/api/v1/pdl/dictionary/version || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
