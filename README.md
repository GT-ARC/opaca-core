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
* go to `POST containers`, click "try it out", and set the `imageName` to `"sample-agent-container-image"`, or copy the entire content from `examples/sample-container/src/main/resources/container.json` (but the other attributes don't actually matter for now)
* in another terminal, do `docker ps` to find the started image, and then `docker logs -f <container-name>` to show (and follow) the logs
* in the Web UI, run the `GET containers` or `GET agents` routes to see the running agents and their actions
* use the `POST send` or `POST invoke` routes to send messages to the agent (with any payload; reply-to does not matter for now), or invoke the agent's dummy action (the action takes some time to run); check the logs of the agent container; you can also invoke the action and then immediately re-send the message to check that both work concurrently
* shut down the platform with Ctrl+C; the agent container(s) should shut down as well

## Getting Started / Kubernetes Environment
* Log in to your Kubernetes (K8s) cluster.
* Apply the document "doc/config/permission.yaml" to your cluster with `kubectl apply -f doc/config/permission.yaml`. This will create a namespace called "agents", a ServiceAccount, and the Roles associated with this ServiceAccount.
* Set the environment variables in the "application.properties" file to "kubernetes" and run `mvn install` in the parent folder to create the JAR file for the platform.
* Build the image for the platform by running the command `docker build -t agents-platform jiacpp-platform/.`
* Log in to your Docker registry and push the platform image to it.
* Also build the sample-agent-container-image with `docker build -t sample-agent-container-image examples/sample-container` and push it to your Docker registry.
* Prepare your document "doc/config/platform-deploy.yaml." Replace the registry for the platform image with your own registry address. Also, replace the registry configurations used by the platform to deploy new agents.
* Create a secret named "my-registry-key" for the registry that contains the platform image. Alternatively, you can use a different name, but then make sure to update the corresponding entry in the "platform-deploy.yaml" file. Use the command: `kubectl create secret docker-registry my-registry-key --docker-server=<address:port> --docker-username=<username> --docker-password='<password>' -n agents`
* Apply the document "config/platform-deploy.yaml" to your cluster.
* Now the pod with the platform is up and running. If you run the command `kubectl get services -n agents` you will see the IP of the service that is mapped to this pod.
* Interact with the platform by using curl commands, such as: `curl -X POST -H "Content-Type: application/json" -d '{"imageName": "<registryAddress>:<registryPort>/sample-agent-container-image"}' http://<IP-platform-service/pod>:8000/containers` However, make sure that the port is correct. The default is 8000


## Environment Variables (Runtime Platform)

The values in the `PlatformConfig` file are read from the `application.properties` file, which in turn are read from similarly named environment variables or from default values if those are not set.

### General
* `PORT` (default: 8000) The port where the Runtime Platform itself exposes its API and Swagger Web UI.
* `PUBLIC_URL` (default: null) Public URL of the Runtime Platform; if not set, it will try to guess its own IP.
* `CONTAINER_TIMEOUT_SEC` (default: 10) Timeout in seconds how long the RP will try to reach a newly started container's `/info` route before it assumes it did not start properly and stops it again.
* `PLATFORM_ENVIRONMENT` (default: "native") The environment where the platform itself is running, which determine the way to find its own IP address and other details.
* `CONTAINER_ENVIRONMENT` (default: "docker") The environment where the Agent Containers should be running; possible values are `docker` and `kubernetes`.
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
* `KUBERNETES_NAMESPACE` (default: "agents")
* `KUBERNETES_CONFIG` (default: "~/.kube/config")

### Security & Authentication
* `ENABLE_AUTH` (default: false) Whether to require token-based authentication on all routes; see [Authentication](doc/auth.md) for details.
* `USERNAME_PLATFORM` (default: null) Name of a single authorized user (temporary)
* `USERNAME_PLATFORM` (default: null) Password of a single authorized user (temporary)

You can set those properties in the run config in your IDE, via an `.env` file, using `export` on the shell or in a `docker-compose.yml` file. Note that if you have one of those properties in e.g. your `.env` file, and it does not have a value, that may still overwrite the default and set the value to `null` or the empty string.

See the [API docs](doc/api.md) for Environment Variables passed from the Runtime Platform to the started Agent Containers.


## Additional Information

* [API Routes and Models](doc/api.md)
* [Protocols](doc/protocols.md)
* [Authentication](doc/auth.md)