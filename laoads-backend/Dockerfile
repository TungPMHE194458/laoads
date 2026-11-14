# ===== Build stage ===== (adapt từ BOC Dockerfile)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Cache deps trước
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
COPY laoads-domain/pom.xml laoads-domain/
COPY laoads-infra/pom.xml laoads-infra/
COPY laoads-api/pom.xml laoads-api/
COPY laoads-web/pom.xml laoads-web/
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline -DskipTests

# Build
COPY lombok.config ./
COPY laoads-domain/src laoads-domain/src
COPY laoads-infra/src  laoads-infra/src
COPY laoads-api/src    laoads-api/src
COPY laoads-web/src    laoads-web/src
RUN ./mvnw -B -q clean package -DskipTests

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S laoads && adduser -S laoads -G laoads
USER laoads

COPY --from=build /workspace/laoads-web/target/laoads-web.jar /app/laoads-web.jar

# uat/prod mặc định 8080 (override SERVER_PORT)
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/laoads-web.jar"]
