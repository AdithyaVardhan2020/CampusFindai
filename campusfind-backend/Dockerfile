# ------------------------------------------------------------------------------
# Build Stage: Compile and Package Spring Boot Application
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Runtime Stage: glibc-based JRE Container for ONNX Runtime Native Compatibility
# ------------------------------------------------------------------------------
# NOTE: Must use glibc-based image (not Alpine musl) because com.microsoft.onnxruntime
# bundles native Linux x86_64 binaries linked against GNU C Library (glibc).
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root user for container security (allocates next available UID/GID, avoiding UID 1000 collision)
RUN if ! id -u appuser >/dev/null 2>&1; then \
        groupadd appuser 2>/dev/null || groupadd -r appuser 2>/dev/null || true; \
        useradd -m -g appuser -s /bin/sh appuser 2>/dev/null || useradd -m -s /bin/sh appuser; \
    fi && chown -R appuser:appuser /app

# Copy bundled ONNX INT8 visual encoder model (~91.6 MB)
COPY --chown=appuser:appuser models/ /app/models/

# Copy packaged JAR from build stage
COPY --from=build --chown=appuser:appuser /workspace/target/campusfind-backend-0.0.1-SNAPSHOT.jar app.jar

USER appuser

# Render assigns dynamic port via $PORT (defaults to 8080)
ENV PORT=8080
ENV CAMPUSFIND_AI_MODEL_PATH=/app/models/openclip_vit_b32_int8.onnx
ENV CAMPUSFIND_AI_CONCURRENCY_LIMIT=3
EXPOSE 8080

# Tune JVM for Render Free's 512 MB memory ceiling:
# - UseSerialGC: Minimizes GC thread overhead and remembered set memory footprint (~30-50 MB saved)
# - Xms128m -Xmx256m: Restricts Java heap to 256 MB max, leaving ~250 MB for ONNX native allocations and Metaspace
# - Xss512k: Reduces thread stack size from default 1MB
# - MaxMetaspaceSize=128m: Bounds class metadata footprint
# - server.address=0.0.0.0 and preferIPv4Stack=true: Guarantees Tomcat binds to 0.0.0.0 on IPv4
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -Xms128m -Xmx256m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -Djava.net.preferIPv4Stack=true -jar app.jar"]
