# Multi-stage build for Spring Boot app
FROM openjdk:17-jdk-slim as builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn
COPY pom.xml .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (this layer will be cached)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM openjdk:17-jre-slim

# Install ImageMagick for image conversion
RUN apt-get update && \
    apt-get install -y imagemagick && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create app user
RUN addgroup --system spring && adduser --system spring --ingroup spring

# Set working directory
WORKDIR /app

# Copy jar from builder stage
COPY --from=builder /app/target/change-my-image-0.0.1-SNAPSHOT.jar app.jar

# Change ownership
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]









