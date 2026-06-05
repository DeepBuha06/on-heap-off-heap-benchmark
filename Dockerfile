# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
# Make the wrapper executable
RUN chmod +x mvnw
# Download dependencies
RUN ./mvnw dependency:go-offline
COPY src ./src
# Build the jar
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the production image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copy the built jar from the builder stage
COPY --from=builder /app/target/kvstore-0.0.1-SNAPSHOT.jar app.jar
# Expose the web port
EXPOSE 8080
# Expose the raw TCP port
EXPOSE 9090
# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
