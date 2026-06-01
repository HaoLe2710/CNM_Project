# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN ./mvnw -DskipTests package
RUN JAR_FILE=$(ls target/*.jar | grep -v 'original' | head -n 1) && cp "$JAR_FILE" /tmp/app.jar

FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

COPY --from=build /tmp/app.jar /app/app.jar

ENV JAVA_OPTS=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
