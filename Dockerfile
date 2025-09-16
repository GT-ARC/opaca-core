# TWO-STAGED DOCKERFILE FOR CI

# STAGE 1: BUILD THE APPLICATION
FROM maven:3.9.11-ibm-semeru-21-noble AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

# STAGE 2: CREATE THE FINAL IMAGE
FROM ibm-semeru-runtimes:open-21-jdk
WORKDIR /app

# copy and run compiled all-in-one Jar
COPY --from=builder /build/opaca-platform/target/opaca-platform-0.3-with-dependencies.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
