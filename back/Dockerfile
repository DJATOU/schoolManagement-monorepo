# Use an official OpenJDK runtime with Java 21 (standard variant, not Alpine)
FROM eclipse-temurin:21-jdk

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file into the container
COPY target/schoolManagement-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your application will run on (8080 by default)
EXPOSE 8080

# Command to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
