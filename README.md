![OPACA-Logo](doc/img/opaca-logo.png)

# OPACA: An Open, Language- and Platform-Independent API for Containerized Agents

Copyright 2022-2024 GT-ARC & DAI-Labor, TU Berlin

* Main Contributors: Tobias Küster and Benjamin Acar
* Further contributions by: Oskar Kupke, Robert Strehlow

This (https://github.com/gt-arc/opaca-core/) is the public repository of the OPACA project. Feel free to create issues if you have any suggestions, or improve things yourself with a fork and pull request. The main development work still happens in the internal/private repository at https://gitlab.dai-labor.de/jiacpp/prototype/, including most (internal) tickets, development branches, merge requests, build pipelines, etc.

See the end of this Readme for publication about the OPACA Framework and API.

Note: The working title of OPACA was 'JIAC++'. Some references to the old name may still be found in the code and documentation.

## Prototype and Reference Implementation

This module provides a first prototype for the "OPACA" API, as well as a reference implementation in Java and an example container. OPACA stands for "Open, Language- and Platform-Independent API for Containerized Agents".

This repository includes software developed in the course of the project "Offenes Innovationslabor KI zur Förderung gemeinwohlorientierter KI-Anwendungen" (aka Go-KI, https://go-ki.org/) funded by the German Federal Ministry of Labour and Social Affairs (BMAS) under the funding reference number DKI.00.00032.21.


## About

The goal of OPACA is to combine multi-agent systems with microservices and container-technologies using a simple, universal API, build around a set of simple design principles and requirements:

* Open, Standardized Interfaces
* Being Language Agnostic
* Modularity and Reusability
* Self-Description
* Dynamic Multi-Tenancy
* Distribution

This way, we make it easier to deploy and connect heterogeneous systems consisting of components implemented using different agent-frameworks and/or non-agent components in different languages.

A multi-agent system in the OPACA approach consists of two types of components:

* **Agent Containers** are containerized applications that implement the OPACA Agent Container API. They provide REST routes for finding out about the agents within the container, and to interact with them by of sending asynchronous messages (unicast and broadcast) and invoking synchronous actions.

* **Runtime Platforms** are used to manage one or more Agent Containers, deploying those in Docker or Kubernetes. They connect different Agent Containers while also providing basic services such as yellow pages, authentication, etc.

Please refer to the [API docs](doc/api.md) page for more information about the different routes provided for the API and the respective requests and responses.


## Modules

* `opaca-platform`: reference implementation of Runtime Platform, done in Java + Spring Boot
* `opaca-container`: reference implementation of Agent Container, done in JIAC VI (Java, Kotlin)
* `opaca-model` interface descriptions and model classes for the API (Java)
* `examples`: sample agent container(s) to be executed on the platform; sample-container is an example that can be used to quickly test platform setup and manually test actions, ping-pong is an example that can be used to test communication between two containers


## Getting Started / Quick Testing Guide

* run `mvn install -DskipTests` in the parent directory to build everything in order (skipping tests is necessary in this step, as the tests would require the Docker image that is built in the next step)
* build the sample container with `docker build -t sample-agent-container-image examples/sample-container`
* optional: run `mvn test` to check that everything is okay
* start the platform with `java -jar opaca-platform/target/jiacpp-platform-<version>-with-dependencies.jar`
* go to <http://localhost:8000/swagger-ui/index.html>
* go to `POST containers`, click "try it out", and set the `imageName` to `"sample-agent-container-image"`, or replace the entire value of `image` by the content from `examples/sample-container/src/main/resources/sample-image.json` (in this case, make sure to also provide values for the required parameters in `arguments`)
* in another terminal, do `docker ps` to find the started image, and then `docker logs -f <container-name>` to show (and follow) the logs
* in the Web UI, run the `GET containers` or `GET agents` routes to see the running agents and their actions
* use the `POST send` or `POST invoke` routes to send messages to the agent (with any payload; reply-to does not matter for now), or invoke the agent's dummy action (the action takes some time to run); check the logs of the agent container; you can also invoke the action and then immediately re-send the message to check that both work concurrently
* shut down the platform with Ctrl+C; the agent container(s) should shut down as well

See [Execution Environments](doc/environments.md) for more information on different ways to execute the platform and agent containers.

Note: The Runtime Platform requires Java version 17 or higher.


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
* `USERNAME_PLATFORM` (default: admin) Name of a single authorized user (temporary)
* `PASSWORD_PLATFORM` (default: "") Password of a single authorized user (temporary)

### User Management MongoDB
* `DB_EMBED` (default: true) Switches between an embedded and external MongoDB.
* `DB_URI` (default: mongodb://user:pass@localhost:27017/admin) Url of the running MongoDB service. If the platform is running in a container itself, use the name of the container running the Mongo service as a host name (e.g. opaca-data). For further details, click [here](doc/user-management.md#mongodb-docker-container).
* `DB_NAME` (default: opaca-user-data) Name of the database which will store user-related information. Only available for the external MongoDB.

You can set those properties in the run config in your IDE, via an `.env` file, using `export` on the shell or in a `docker-compose.yml` file. Note that if you have one of those properties in e.g. your `.env` file, and it does not have a value, that may still overwrite the default and set the value to `null` or the empty string.

See the [API docs](doc/api.md) for Environment Variables passed from the Runtime Platform to the started Agent Containers.


## Releases & Distribution

This section is about distributing releases to the `dai-open` and `dai-open-snapshot` repositories (see `pom.xml` for details). This is necessary so that others can use the modules as libraries without having to check out the repository and build it locally.

New SNAPSHOT releases are deployed by CI each time a new commit is pushed to the main branch (usually by a merge request). Those a deployed to the `dai-open-snapshot` repository. New non-SNAPSHOT releases are created manually and deployed to `dai-open` every time there is a significant change (preferably _before_ that change is merged) or when a larger number of smaller changes have accumulated, by the following steps:

* increment the version numbers in _all_ `pom.xml` files to the next release version, e.g. change `X.Y-SNAPSHOT` to `X.Y` (both the `version` and `parent.version`, and don't forget the examples)
* rename the current `X.Y-SNAPSHOT` sections in the changelog file to `X.Y`
* run `mvn deploy` in the repository root (for this step, you will need the appropriate credentials in vour `~/.m2/settings.xml`)
* make a Git commit and `git tag` it as `release-x.y`
* increment the version numbers from `X.Y` to `X.Y+1-SNAPSHOT` (or, for very significant changes, `X+1.0-SNAPSHOT`)
* create a new section for the new snapshot version in the changelog file


## Additional Information

* [API Routes and Models](doc/api.md)
* [Protocols](doc/protocols.md)
* [Execution Environments](doc/environments.md)
* [Session Handling](doc/session.md)
* [Authentication](doc/auth.md)
* [User Management](doc/user-management.md)
* [Parameter Validation](doc/validation.md)


## Publications

* B. Acar et al., "OPACA: Toward an Open, Language- and Platform-Independent API for Containerized Agents," in IEEE Access, vol. 12, pp. 10012-10022, 2024, doi: [10.1109/ACCESS.2024.3353613](https://doi.org/10.1109/ACCESS.2024.3353613).
