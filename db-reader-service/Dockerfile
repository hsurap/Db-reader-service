FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/db-reader-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
