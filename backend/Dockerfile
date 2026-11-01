# Multi-stage build for Spring Boot app
# Builder on the same LTS as the runtime; java.version 17 keeps the bytecode target.
FROM eclipse-temurin:25-jdk-alpine AS builder

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

# Runtime stage: Ubuntu Jammy; Java 25 JRE per Snyk recommendation (jar built with 17 runs on 25)
FROM eclipse-temurin:25.0.4.1_1-jre-jammy

# Install ImageMagick 6 (convert, identify), curl, HEIC/WebP deps. No Alpine = no Alpine zlib/libpng vulns.
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    imagemagick \
    libheif1 \
    curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && which convert && which identify && convert -version | head -1

# Create app user (Debian/Ubuntu)
# Distrust eMudhra (emSign) roots in Java's own trust store: their intermediate keys were
# compromised (Oct 2026) and Java ignores the OS ca-certificates bundle.
RUN keytool -list -v -cacerts -storepass changeit | sed -n 's/^Alias name: //p' | grep -i emsign \
    | while IFS= read -r a; do keytool -delete -cacerts -storepass changeit -alias "$a"; done \
    && ! keytool -list -cacerts -storepass changeit | grep -qi emsign
RUN groupadd --system spring && useradd --system --gid spring spring

# Set working directory
WORKDIR /app

# Policy file comes from builder image (same tree as COPY src); avoids a second context COPY.
COPY --from=builder /app/src/main/resources/imagemagick-policy.xml /etc/ImageMagick-6/policy.xml

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









