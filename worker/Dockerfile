FROM eclipse-temurin:11-jdk-alpine AS build
WORKDIR /app
COPY . .
# Giving execute permission to gradle wrapper
RUN chmod +x gradlew
RUN ./gradlew :worker:bootJar --no-daemon

FROM eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY --from=build /app/worker/build/libs/worker*.jar /app/worker.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/worker.jar"]
