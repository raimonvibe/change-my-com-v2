# Multi-stage build for Spring Boot app
FROM eclipse-temurin:17-jdk-alpine as builder

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

# Runtime stage: use Ubuntu Jammy to avoid Alpine zlib/libpng CVEs (Snyk still reports them with apk upgrade)
FROM eclipse-temurin:17-jre-jammy

# Install ImageMagick 6 (convert), curl, HEIC/WebP deps. No Alpine = no Alpine zlib/libpng vulns.
RUN apt-get update && apt-get install -y --no-install-recommends \
    imagemagick \
    libheif1 \
    curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create app user (Debian/Ubuntu)
RUN groupadd --system spring && useradd --system --gid spring spring

# Set working directory
WORKDIR /app

# Copy ImageMagick security policy (Jammy uses ImageMagick 6; policy path is -6)
COPY src/main/resources/imagemagick-policy.xml /etc/ImageMagick-6/policy.xml

# Create logs directory and set permissions
RUN mkdir -p /app/logs && chown -R spring:spring /app

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









