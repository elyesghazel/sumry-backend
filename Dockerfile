# -----------------------------------------------------------------------------
# STAGE 1: Build (Java 21, with Gradle cache and tools)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install build tools
RUN apk add --no-cache bash curl unzip dos2unix

# Gradle cache directory
ENV GRADLE_USER_HOME=/gradle-cache
RUN mkdir -p /gradle-cache

# Copy Gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Fix line endings and make the wrapper executable
RUN dos2unix gradlew || true
RUN chmod +x gradlew

# Copy source code
COPY src src

# Build the boot jar using the Gradle cache
RUN ./gradlew bootJar -x test --no-daemon --gradle-user-home $GRADLE_USER_HOME

# -----------------------------------------------------------------------------
# STAGE 2: Runtime (Java 21)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the jar
COPY --from=build /app/build/libs/*.jar app.jar

# The API needs nothing root can do; a compromise of the JVM should not own the container.
RUN addgroup -S app && adduser -S -G app app
USER app

EXPOSE 48090

ENTRYPOINT ["java", "-jar", "app.jar"]
