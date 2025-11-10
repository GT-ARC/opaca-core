# Build as `docker build -t sample-agent-container-image .`
FROM ibm-semeru-runtimes:open-21-jdk
ADD ./target/appassembler/ /app
ENTRYPOINT ["/app/bin/main-kt"]
