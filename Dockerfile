# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S flashsale && adduser -S flashsale -G flashsale
WORKDIR /app
COPY --from=build /workspace/target/flash-sale-*.jar app.jar
USER flashsale
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
