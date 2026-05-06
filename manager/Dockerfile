FROM eclipse-temurin:11-jdk-alpine AS build
WORKDIR /app
COPY . .
# Giving execute permission to gradle wrapper
RUN chmod +x gradlew
RUN ./gradlew :manager:bootJar --no-daemon

FROM eclipse-temurin:11-jre-alpine
RUN apk add --no-cache curl
RUN curl -L https://github.com/grafana/k6/releases/download/v0.49.0/k6-v0.49.0-linux-amd64.tar.gz | tar -xz && \
    mv k6-v0.49.0-linux-amd64/k6 /usr/bin/k6 && \
    rm -rf k6-v0.49.0-linux-amd64
WORKDIR /app
COPY --from=build /app/manager/build/libs/manager*.jar /app/manager.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/manager.jar"]
