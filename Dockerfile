# Use the official Spring Boot image as a base
FROM openjdk:21-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the build artifacts
COPY build/libs/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]