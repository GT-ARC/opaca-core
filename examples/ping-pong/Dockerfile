# Build as `docker build -t sample-agent-container-image .`
FROM openjdk:19-slim
ADD ./target/appassembler/ /app

# use this to create ping-container: docker build -t ping-container-image .
ENTRYPOINT ["/app/bin/main-kt", "ping"]

# use this to create pong-container: docker build -t pong-container-image .
#ENTRYPOINT ["/app/bin/main-kt", "pong"]
