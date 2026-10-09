FROM node:22-alpine AS frontend
WORKDIR /workspace/Frontend
COPY Frontend/package.json Frontend/package-lock.json ./
RUN npm ci
COPY Frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
COPY --from=frontend /workspace/Frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/rag-ingestor-0.1.0.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD sh -c 'curl --fail --silent "http://localhost:${PORT:-8080}/actuator/health/readiness" || exit 1'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
