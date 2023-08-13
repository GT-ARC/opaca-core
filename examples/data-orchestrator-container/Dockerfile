# Build as `docker build -t sample-agent-container-image .`
FROM openjdk:19-slim
RUN apt-get update && apt-get install -y ffmpeg
ADD ./target/appassembler/ /app
ENTRYPOINT ["/app/bin/main-kt"]
