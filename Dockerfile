# Use the official Spring Boot image as a base
FROM openjdk:21-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the application code into the container
COPY . /app

RUN chmod +x ./gradlew
RUN ./gradlew build  # Replace with Maven if applicable

# Expose the port the app runs on
EXPOSE 10000

# Run the application
ENTRYPOINT ["java", "-jar", "build/libs/is-v2-0.0.1-SNAPSHOT.jar"]