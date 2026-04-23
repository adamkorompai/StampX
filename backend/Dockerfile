# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Download dependencies first — cached as a separate layer so source changes are fast
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Minimal JRE runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S stampx && adduser -S stampx -G stampx

COPY --from=builder /app/target/*.jar app.jar

USER stampx
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", "app.jar"]
