FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew settings.gradle ./
COPY gradle ./gradle
COPY common/build.gradle ./common/build.gradle
COPY app-main/build.gradle ./app-main/build.gradle
COPY app-collector/build.gradle ./app-collector/build.gradle

COPY common/src ./common/src
COPY app-collector/src ./app-collector/src

RUN chmod +x ./gradlew && ./gradlew :collector:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /workspace/app-collector/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
