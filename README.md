# Prototype for JIAC++ API and Reference Implementation

This module provides a first prototype for the "JIAC++" API,
as well as a reference implementation in Java and an example container.

This is in no way meant to be final, but rather to provide a basis for further discussions
and first tests in order to find out what of this makes sense etc.


## Modules

* `jiacpp-platform`: reference implementation of Runtime Platform, done in Java + Spring Boot
* `jiacpp-container`: reference implementation of Agent Container, done in JIAC VI (Java, Kotlin)
* `jiacpp-model` interface descriptions and model classes for the API (Java)
* `examples`: sample agent container(s) to be executed on the platform; sample-container is an example that can be used to quickly test platform setup and manually test actions, ping-pong is an example that can be used to test communication between two containers


## Getting Started / Quick Testing Guide

* run `mvn install` in the parent directory to build everything in order
* build the sample container with `docker build -t sample-agent-container-image examples/sample-container`
* start the platform with `java -jar jiacpp-platform/target/jiacpp-platform-0.1-SNAPSHOT.jar`
* go to <http://localhost:8000/swagger-ui/index.html>
* go to `POST containers`, click "try it out", and set the `imageName` to `"sample-agent-container-image"`, or replace the entire value of `image` by the content from `examples/sample-container/src/main/resources/container.json` (in this case, make sure to also provide values for the required parameters in `arguments`)
* in another terminal, do `docker ps` to find the started image, and then `docker logs -f <container-name>` to show (and follow) the logs
* in the Web UI, run the `GET containers` or `GET agents` routes to see the running agents and their actions
* use the `POST send` or `POST invoke` routes to send messages to the agent (with any payload; reply-to does not matter for now), or invoke the agent's dummy action (the action takes some time to run); check the logs of the agent container; you can also invoke the action and then immediately re-send the message to check that both work concurrently
* shut down the platform with Ctrl+C; the agent container(s) should shut down as well

See [Execution Environments](doc/environments.md) for more information on different ways to execute the platform and agent containers.


## Environment Variables (Runtime Platform)

The values in the `PlatformConfig` file are read from the `application.properties` file, which in turn are read from similarly named environment variables or from default values if those are not set.

### General
* `PORT` (default: 8000) The port where the Runtime Platform itself exposes its API and Swagger Web UI.
* `PUBLIC_URL` (default: null) Public URL of the Runtime Platform, including protocol and port; if not set, it will try to guess its own IP.
* `CONTAINER_TIMEOUT_SEC` (default: 10) Timeout in seconds how long the RP will try to reach a newly started container's `/info` route before it assumes it did not start properly and stops it again.
* `PLATFORM_ENVIRONMENT` (default: "native") The environment where the platform itself is running, which determine the way to find its own IP address and other details.
* `CONTAINER_ENVIRONMENT` (default: "docker") The environment where the Agent Containers should be running; possible values are `docker` and `kubernetes`.
* `SESSION_POLICY` (default: "shutdown") How to behave when the platform is shut down and restarted. See [Session](doc/session.md) for details.
* `DEFAULT_IMAGE_DIRECTORY` (default: null) The runtime platform will try to read any JSON files from this directory containing Agent Container Image descriptions and auto-deploy those to the platform when it starts.

### Image Registry Credentials
* `REGISTRY_SEPARATOR` (default: ";") Separator for the below attributes for registry credentials.
* `REGISTRY_NAMES` (default: empty) Known Docker registry names, segment before the first `/` as it appears in image names, without protocol.
* `REGISTRY_LOGINS` (default: empty) Login names for each of the above registries (number has to match).
* `REGISTRY_PASSWORDS` (default: empty) Passwords for each of the above registries (number has to match).

### Docker
* `REMOTE_DOCKER_HOST` (default: null) Remote Docker host (just IP or alias, no protocol or port); if not set, the platform will start containers on the local Docker.
* `REMOTE_DOCKER_PORT` (default: 2375) Port where remote Docker host exposes its API; usually this is 2375.

### Kubernetes
* `KUBERNETES_NAMESPACE` (default: "agents") Namespace where to deploy Agent Container pods.
* `KUBERNETES_CONFIG` (default: "~/.kube/config") Alternative location for Kubernetes config.

### Security & Authentication
* `ENABLE_AUTH` (default: false) Whether to require token-based authentication on all routes; see [Authentication](doc/auth.md) for details.
* `SECRET` (default: empty) The secret used to encrypt and decrypt the JWT tokens used for authentication.
* `USERNAME_PLATFORM` (default: null) Name of a single authorized user (temporary)
* `USERNAME_PLATFORM` (default: null) Password of a single authorized user (temporary)

You can set those properties in the run config in your IDE, via an `.env` file, using `export` on the shell or in a `docker-compose.yml` file. Note that if you have one of those properties in e.g. your `.env` file, and it does not have a value, that may still overwrite the default and set the value to `null` or the empty string.

See the [API docs](doc/api.md) for Environment Variables passed from the Runtime Platform to the started Agent Containers.


## Additional Information

* [API Routes and Models](doc/api.md)
* [Protocols](doc/protocols.md)
* [Execution Environments](doc/environments.md)
* [Session Handling](doc/session.md)
* [Authentication](doc/auth.md)
