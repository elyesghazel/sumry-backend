# -----------------------------------------------------------------------------
# STAGE 1: Build (Java 21, mit Gradle Cache & Tools)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Tools installieren
RUN apk add --no-cache bash curl unzip dos2unix

# Gradle Cache Verzeichnis
ENV GRADLE_USER_HOME=/gradle-cache
RUN mkdir -p /gradle-cache

# Gradle Dateien kopieren
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Line-Endings fixen & ausführbar machen
RUN dos2unix gradlew || true
RUN chmod +x gradlew

# Source Code kopieren
COPY src src

# Build: BootJar, Gradle Wrapper Cache nutzen
RUN ./gradlew bootJar -x test --no-daemon --gradle-user-home $GRADLE_USER_HOME

# -----------------------------------------------------------------------------
# STAGE 2: Runtime (Java 21)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Jar kopieren
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 48090

ENTRYPOINT ["java", "-jar", "app.jar"]
