# Ping Pong Example

Simple distributed example for testing communication between different AgentContainer instances, between
AgentContainer and RuntimePlatform and back, and between different RuntimePlatforms.

The general flow of messages is as follows:

* in regular intervals, Ping agent sends a Broadcast message to all Pong agents
* Pong agents reply with an offer (just a random number) in a Directed message
* Ping agent evaluates all offers and sends an Invoke message to the Pong agent with the best offer

With this, all three messaging mechanisms -- directed, broadcast, and invoke -- can be tested in different scenarios:

* Ping and Pong agent(s) in the same AgentContainer
* Ping and Pong in different AgentContainers under the same RuntimePlatform
* Ping and Pong in different AgentContainers under different RuntimePlatform

In each case, the messages are sent first to the ContainerAgent, who then redirects them accordingly.
(In the case that both are in the same container, they could also communicate directly with each others,
but that's not what we want to test.)

The Main.kt file has to be adapted to either start all agents in the same container, or start either a single Ping
or a single Pong agent depending on command line parameters (which are passed in the Dockerfile, to be adapted 
before calling `docker build`).

Starting process for both-in-same-container-case:

* start runtime platform
* go to pingpong directory
* run `mvn install`
* run `docker build -t ping-pong-container-image .`
* run `curl -X 'POST' 'http://localhost:8000/containers' -H 'Content-Type: application/json' -d '{"imageName": "ping-pong-container-image"}'`
* check container name with `docker ps`, then see logs with `docker logs -f <container_namw>`
