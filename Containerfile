FROM gradle:9.3.1-jdk21 AS build
WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle build.gradle ./
COPY src src

RUN chmod +x gradlew && ./gradlew --no-daemon clean installDist

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV APP_ENV=prod
ENV PORT=7000

COPY --from=build /workspace/build/install/config-server /app/

EXPOSE 7000
ENTRYPOINT ["/app/bin/config-server"]
