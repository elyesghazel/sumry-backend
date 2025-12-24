# -----------------------------------------------------------------------------
# STAGE 1: Build (Jetz mit Java 21!)
# -----------------------------------------------------------------------------
# WICHTIG: Hier stand vorher 17, jetzt 21
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Gradle Dateien kopieren
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Line-Endings fixen (Windows -> Linux)
RUN dos2unix gradlew || true
RUN chmod +x gradlew

# Source Code kopieren
COPY src src

# Bauen
RUN ./gradlew bootJar -x test --no-daemon

# -----------------------------------------------------------------------------
# STAGE 2: Runtime (Auch hier Java 21!)
# -----------------------------------------------------------------------------
# WICHTIG: Hier stand vorher 17, jetzt 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Jar kopieren
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
