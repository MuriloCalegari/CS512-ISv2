# Stage 1: Build the application with Gradle
FROM openjdk:21-jdk-slim AS build-stage

# Set the working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

# Pre-fetch dependencies to leverage caching
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

# Copy the rest of the application source code
COPY . /app

# Build the application
RUN chmod +x ./gradlew
RUN ./gradlew build

# Stage 2: Create a minimal runtime image
FROM openjdk:21-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the built application from the build stage
COPY --from=build-stage /app/build/libs/is-v2-0.0.1-SNAPSHOT.jar /app/app.jar

# Expose the application's port
EXPOSE 10000

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
