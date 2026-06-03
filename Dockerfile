# Stage 1: Build the application
FROM maven:3.8.8-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml and download dependencies to cache them
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build package
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/smartshala-0.0.1-SNAPSHOT.jar app.jar

# Expose port 3000 (configured in application.properties)
EXPOSE 3000

# Set entrypoint to run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
