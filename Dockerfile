# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage. Full JDK and the Maven wrapper, so the image needs no local
# Java or Maven install — "docker compose up" is the only prerequisite.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# The wrapper and the pom first. This layer is rebuilt only when dependencies
# change, so an ordinary source edit does not re-resolve the whole tree.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Tests need Postgres, Redis and RabbitMQ, none of which exist during a build;
# they run against the compose stack instead. See README-DOCKER.md.
RUN ./mvnw -B -ntp clean package -DskipTests

# ---------------------------------------------------------------------------
# Runtime stage. JRE only — no compiler, no build tools, no source.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# curl is here for the container healthcheck and nothing else.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Runs unprivileged: a container process has no business being root (OWASP A02).
#
# The upload directory is created and chowned HERE, as root, before the USER
# switch. Docker seeds a fresh named volume from whatever the image holds at the
# mount point, ownership included — so without this the volume arrives owned by
# root, and the app cannot create the avatars/ subdirectory inside it.
RUN useradd --system --create-home --uid 10001 spring \
    && mkdir -p /data/uploads \
    && chown -R spring:spring /data
USER spring
WORKDIR /app

COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

# Where local-driver uploads land. Declared so an unmounted run still works,
# though compose mounts a named volume over it to survive a container replace.
VOLUME ["/data/uploads"]

EXPOSE 8080

# 120s start-period: Boot + Flyway + Hibernate validation on a 1-vCPU box takes
# longer than 60s, and a container flagged unhealthy on every deploy hides the
# one time it really is.
HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --retries=10 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# MaxRAMPercentage rather than a fixed -Xmx: the JVM sizes itself from the
# container's cgroup limit (docker-compose.yml sets one), not from the host.
# Serial GC because the container gets 1-2 vCPUs and under 1 GB: one collector
# thread beats G1's background threads contending with Tomcat for the same
# core. ExitOnOutOfMemoryError so `restart: unless-stopped` replaces a dead
# JVM instead of leaving a limping one behind.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=70", \
    "-XX:+UseSerialGC", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "/app/app.jar"]
