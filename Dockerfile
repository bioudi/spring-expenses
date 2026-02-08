# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
RUN apk add --no-cache nodejs npm
WORKDIR /app
COPY pom.xml .
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN mvn dependency:go-offline -B
RUN cd frontend && npm ci
COPY src ./src
COPY frontend ./frontend
RUN mvn package -DskipTests -B -Dskip.npm.install=true

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup
COPY --from=build /app/target/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-Duser.timezone=America/Montreal", "-jar", "app.jar"]
