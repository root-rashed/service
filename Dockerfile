# syntax=docker/dockerfile:1

# ---------- Build ----------
# A separate stage so the JDK, Maven and the downloaded dependencies never reach the runtime image.
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copying the descriptor first means the dependency layer is reused on every source-only change,
# which is the difference between a five second and a two minute rebuild.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Runtime ----------
FROM eclipse-temurin:17-jre-jammy

# curl is here only so the container healthcheck can ask the app how it is; wget would pull in
# more than is needed for one request.
RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/*

# The app never signs in, so a fixed non-root user is enough and avoids the uid mismatch that
# makes bind-mounted upload directories unwritable.
RUN groupadd --system --gid 1001 servizone \
    && useradd --system --uid 1001 --gid servizone --create-home --home-dir /home/servizone servizone

WORKDIR /app

COPY --from=build --chown=servizone:servizone /build/target/*.jar app.jar

# Uploaded service and profile images. Declared as a volume so the directory exists, is owned by
# the app user and survives a container replacement.
RUN mkdir -p /app/storage/public \
    && chown -R servizone:servizone /app/storage
VOLUME ["/app/storage"]

USER servizone

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_PATH=/app/storage \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]