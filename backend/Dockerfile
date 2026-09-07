# ---- Build stage -------------------------------------------------------------
# Compiles with JDK 21 (the version the project targets) and produces the jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Free tiers are memory-constrained (Render's is 512 MB). Left unbounded the JVM
# sizes its heap against the host's total memory, not the container's limit, and
# the build is killed part-way through with no useful error.
ENV MAVEN_OPTS="-Xmx400m -XX:MaxMetaspaceSize=160m"

# Copy the POM first so dependency resolution becomes its own cached layer:
# a source-only change then skips the slow download entirely.
COPY pom.xml .

# Resolve dependencies ahead of the build. dependency:go-offline is strict — it
# fails the build if any plugin cannot be pre-resolved, which is common with
# Spring Boot's plugin set. This is only a caching optimisation, so a failure
# here must not fail the build: fall back to letting the package step fetch what
# it needs.
RUN mvn -B dependency:resolve -DincludeScope=runtime || true

COPY src ./src

# Tests are skipped deliberately: they read rate-book and sample-tender PDFs that
# are not in the repository, so they cannot pass inside the image. They run
# locally and in CI where those files exist.
RUN mvn -B package -DskipTests

# ---- Runtime stage -----------------------------------------------------------
# Only the JRE and the jar ship to production: smaller image, smaller attack surface.
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

# MaxRAMPercentage keeps the heap inside the container's limit rather than the
# JVM sizing itself against the host, which is what causes surprise OOM kills.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=70 -Dserver.port=${PORT} -jar app.jar"]
