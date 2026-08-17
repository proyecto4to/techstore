FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 1000 techstore && \
    adduser -D -u 1000 -G techstore techstore && \
    mkdir -p /data/uploads && \
    chown -R techstore:techstore /data/uploads
RUN apk add --no-cache curl
COPY --from=builder --chown=techstore:techstore /app/target/techstore-backend-*.jar app.jar
ENV APP_UPLOADS_DIR=/data/uploads
USER techstore
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
