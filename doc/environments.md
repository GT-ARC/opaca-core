# Environments

Both the Runtime Platform and the Agent Containers can be executed in different environments. Which environment should be used is controlled using the `PLATFORM_ENVIRONMENT` and `CONTAINER_ENVIRONMENT` environment variables. Where the Platform and Containers are running should in general have no effect on their behavior, and execution environments for Runtime Platform and Agent Containers can be combined freely.

When starting an AgentContainer, a `clientConfig` can be provided, determining certain parameters of the Container Environment. This config is optional, but if it is provided, it has to match the Container Environment in use.


## Platform Environments

### Native

The simplest way to execute the Runtime Platform. Just follow the steps in the "Quick Testing Guide" of the main [Readme](../README.md).

Native execution has been tested to work on Windows, Mac and Linux. If it does not work, please try the Docker environment below.

### Docker

Use this to execute the Runtime Platform in Docker-Compose; see `docker-compose.yml` in the `opaca-platform` directory. Note that at the moment it is not possible to resolve the host's IP address, needed for the communication between Runtime Platform and Agent Containers, from within the Docker container. To work around this problem, use the `PUBLIC_URL` environment variable to set the proper (internal) IP or actual public URL of the Runtime Platform's host system.

For testing purposes, you can get the correct IP address from `ifconfig` (on Linux and Mac) before starting the platform, using a shell-script like the following, where `$INTERFACE` is the name of the network interface to use. Note that the output of `ifconfig` can be platform dependant and the script may need to be adapted accordingly.

```
IP=`ifconfig "$INTERFACE" | grep -o -P "(\d{1,3}\.){3}\d{1,3}" | head -1`
export PUBLIC_URL="http://$IP:8000"
docker-compose up --build
```

### Kubernetes

For productive use, the Runtime Platform itself can be executed in Kubernetes along with its containers.

* Log in to your Kubernetes (K8s) cluster.
* Apply the document "doc/config/permission.yaml" to your cluster with `kubectl apply -f doc/config/permission.yaml`. This will create a namespace called "agents", a ServiceAccount, and the Roles associated with this ServiceAccount.
* Set the environment variables in the "application.properties" file to "kubernetes" and run `mvn install` in the parent folder to create the JAR file for the platform.
* Build the image for the platform by running the command `docker build -t agents-platform opaca-platform/.`
* Log in to your Docker registry and push the platform image to it.
* Also build the sample-agent-container-image with `docker build -t sample-agent-container-image examples/sample-container` and push it to your Docker registry.
* Prepare your document "doc/config/platform-deploy.yaml." Replace the registry for the platform image with your own registry address. Also, replace the registry configurations used by the platform to deploy new agents.
* Create a secret named "my-registry-key" for the registry that contains the platform image. Alternatively, you can use a different name, but then make sure to update the corresponding entry in the "platform-deploy.yaml" file. Use the command: `kubectl create secret docker-registry my-registry-key --docker-server=<address:port> --docker-username=<username> --docker-password='<password>' -n agents`
* Apply the document "config/platform-deploy.yaml" to your cluster.
* Now the pod with the platform is up and running. If you run the command `kubectl get services -n agents` you will see the IP of the service that is mapped to this pod.
* Interact with the platform by using curl commands, such as: `curl -X POST -H "Content-Type: application/json" -d '{"imageName": "<registryAddress>:<registryPort>/sample-agent-container-image"}' http://<IP-platform-service/pod>:8000/containers` However, make sure that the port is correct. The default is 8000


## Container Environments

### Docker

The default execution environment is to run the Agent Containers on a Docker installed on the same host where the Runtime Platform itself is running. Of course, this requires Docker to be installed and accessible via the API on the host, i.e. via `npipe:////./pipe/docker_engine` on Windows and `unix:///var/run/docker.sock` on Mac and Linux. Alternatively, the `REMOTE_DOCKER_HOST`, `REMOTE_DOCKER_PORT` environment variables can be used to configure a remote Docker host.

The typical use case for this is development and testing, but it can also be used for productive use, especially if no Kubernetes is available.

Keep in mind that, when running the Agent Containers on a remote Docker or Kubernetes host, the Runtime Platform itself must also be accessible to the Agent Containers, otherwise they will not be able to route any outgoing communication to other Agent Containers over the platform (but otherwise they will run just fine).

### Kubernetes

For productive use, this is the preferred execution environment. The Runtime Platform will use the default Kubernetes Config (or an alternative config provided in the `KUBERNETES_CONFIG` environment variable) to connect to a Kubernetes cluster and start the Agent Container pods in the namespace given in the `KUBERNETES_NAMESPACE` variable.

## Operating System

### Mac
To use the API on Mac, make sure to set up your Docker properly. The following configuration is tested and works fine:
```
Client:
 Cloud integration: v1.0.35+desktop.5
 Version:           24.0.6
 API version:       1.43
 Go version:        go1.20.7
 OS/Arch:           darwin/arm64
 Context:           desktop-linux

Server: Docker Desktop 4.25.0 (126437)
 Engine:
  Version:          24.0.6
  API version:      1.43 (minimum version 1.12)
  Go version:       go1.20.7
  Git commit:       1a79695
  OS/Arch:          linux/arm64
 containerd:
  Version:          1.6.22
 runc:
  Version:          1.1.8
 docker-init:
  Version:          0.19.0
```