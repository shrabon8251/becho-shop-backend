# Production runtime image for the Becho backend.
#
# Build the JAR first on your own machine (see deploy.sh):
#     mvn -DskipTests package
# Then this image only needs the Java 25 runtime — no Maven inside Docker.

FROM eclipse-temurin:25-jre

# Run as non-root user
RUN useradd -m -u 10001 appuser
WORKDIR /app

COPY target/becho-saas-*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser

EXPOSE 8080

# Flyway migrations (src/main/resources/db/migration) run automatically
# on startup because spring.flyway.enabled=true in application.yml.
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
