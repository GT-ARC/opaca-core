FROM openjdk:21

COPY target/opaca-platform-0.3-SNAPSHOT-with-dependencies.jar /app/app.jar
WORKDIR /app
CMD ["java", "-jar", "app.jar"]
