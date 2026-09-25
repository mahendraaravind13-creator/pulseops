# Stage 1: build the jar. pom.xml is copied first so the dependency layer is cached
# and only re-downloaded when dependencies change, not on every source edit.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Stage 2: run on a slim JRE only (no Maven, no sources), as a non-root user.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S pulseops && adduser -S pulseops -G pulseops
COPY --from=build /build/target/pulseops.jar app.jar
USER pulseops
EXPOSE 8080
# MaxRAMPercentage lets the JVM size its heap from the container memory limit instead of the host's RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
