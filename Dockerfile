# Stage 1: Build the Spring Boot application using Maven and Java 21
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy project files
COPY . .

# Package the standalone Spring Boot JAR (skipping unit tests during Docker build)
WORKDIR /app/backend
RUN mvn clean package -DskipTests

# Stage 2: Minimal, lightweight production runtime container with JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the executable JAR from the builder stage
COPY --from=builder /app/backend/target/*.jar app.jar

# Render dynamically passes $PORT; default to 8080 for local execution
ENV PORT=8080
EXPOSE ${PORT}

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
