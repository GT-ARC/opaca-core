FROM ibm-semeru-runtimes:open-21-jdk

COPY target/opaca-platform-0.4-SNAPSHOT-with-dependencies.jar /app/app.jar
WORKDIR /app
CMD ["java", "-jar", "app.jar"]
