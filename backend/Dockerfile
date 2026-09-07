# ---- Build stage -------------------------------------------------------------
# Compiles the app with JDK 21 (the version the project targets) and produces the jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first so dependency resolution is cached as its own layer: rebuilds
# after a source-only change skip the (slow) dependency download entirely.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests need the rate-book and sample-tender PDFs, which are not part of the image.
RUN mvn -B -q package -DskipTests

# ---- Runtime stage -----------------------------------------------------------
# Only the JRE and the jar ship to production - a much smaller, smaller-attack-surface image.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S raiec && adduser -S raiec -G raiec
COPY --from=build /build/target/*.jar app.jar
RUN chown raiec:raiec app.jar
USER raiec

# Render (and most PaaS hosts) inject the port to bind via $PORT.
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage keeps the heap inside the container's memory limit instead of the
# JVM sizing itself against the host, which is what causes surprise OOM kills on free tiers.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT} -jar app.jar"]
