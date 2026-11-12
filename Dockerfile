# Stage 1: Build executable Spring Boot JAR using Java 25 and the Gradle wrapper
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# Copy Gradle wrapper and build configuration to cache dependency downloads
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

# Ensure Linux execute permissions on the Gradle wrapper script
RUN chmod +x ./gradlew

# Pre-fetch project dependencies to leverage Docker layer caching
RUN ./gradlew dependencies --no-daemon

# Copy application source code and compile the executable JAR
COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Minimal runtime image with OpenJDK 25 JRE
FROM eclipse-temurin:25-jre AS runner
WORKDIR /app

# Run as a dedicated non-root system user for security hardening
RUN groupadd -r spring && useradd -r -g spring spring \
    && mkdir -p /app/uploads/attachments \
    && chown -R spring:spring /app/uploads
USER spring:spring

# Copy the generated Spring Boot fat JAR from the builder stage
COPY --from=builder --chown=spring:spring /workspace/build/libs/ai-support-platform-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
