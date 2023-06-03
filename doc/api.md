# API Routes and Models

This document shows a high-level, easy-to-read, language-agnostic overview of the JIAC++ API, the different routes, etc. It _should_ be kept up to date, but might not always _be_ up to date. When in doubt, please consult the Interfaces and Model classes in the `jiacpp-model` module, or just start a Runtime Platform and check the documentation in the Swagger Web UI.


<!-- TODO environment variables passed from TP to AC -->


## Agents API

* provided by the Agent Container, and by the Runtime Platform (the latter just forwarding to the former)

### `GET /info`

* get information about this agent container
* input: none
* output: `AgentContainer`
* errors: none

### `GET /agents`

* get all agents running in the agent container, or in all agent containers of the platform
* input: none
* output: `[ AgentDescription ]`
* errors: none

### `GET /agents/{agent}`

* get description of a specific agent
* input: ID of the agent to get
* output: `AgentDescription`
* errors: 200 / null result for unknown agent

### `POST /send/{agent}?forward=true|false`

* send asynchronous message to agent
* input: ID of the agent to send the message to
* body: `Message`
* output: none
* errors: 404 for unknown agent

### `POST /broadcast/{channel}?forward=true|false`

* send asynchronous message to all agents subscribed to the channel
* input: name of the message channel
* body: `Message`
* output: none
* errors: none

### `POST /invoke/{action}/{agent}?forward=true|false`

* invoke action/service provided by the given agent and get result (synchronously) 
* expected parameter and output types are given in the action description
* input: name of the action and ID of the agent
* body: JSON object mapping parameter names to parameters
* output: result of the action
* errors: 404 for unknown action or agent

### `POST /invoke/{action}?forward=true|false`

* same as `POST /invoke/{action}/{agent}`, but invoke action at _any_ agent that provides it


## Platform API

* provided by the Runtime Platform, in addition to the above routes (which are just forwarded to the Agent Container)
* a part of those routes may also be provided by an "Agent Bundle" as an in-between of Agent Container and Runtime Platform, still t.b.d.


### `GET /info`

* get information about this runtime platform
* input: none
* output: `RuntimePlatform`
* errors: none

### `GET /containers`

* get list of agent containers currently running on this platform
* input: none
* output: `[ AgentContainer ]`
* errors: none

### `GET /containers/{container}`

* get information on a specific agent container
* input: ID of the agent container
* output: `AgentContainer`
* errors: 200 / null for unknown container

### `POST /containers`

* deploy new Agent Container onto this platform
* body: `AgentContainerImage`
* output: ID of the created AgentContainer (string)
* errors: 404 if image not found, 502 (bad gateway) if container did not start properly

### `POST /containers/notify`

* notify the platform about changes in one of its containers
* body: `containerId` of the container
* output: true or false, depending on whether the container responded to `/info` call; if it does not respond, container is removed
* errors: 404 if container does not exist on platform

### `DELETE /containers/{container}`

* stop/delete/undeploy AgentContainer with given ID from the platform
* input: ID of the agent container to remove
* output: `true/false` whether the container could be removed or not (not found)
* errors: none

### `GET /connections`

* get list of other Runtime Platforms this platform is connected to (just the base URL, not full info)
* input: none
* output: `[ url ]`
* errors: none

### `POST /connections`

* connect platform to another remote Runtime Platform (both directions)
* body: the base URL of that other Runtime Platform
* output: `true/false` whether the platform was newly connected or already known
* errors: 502 (bad gateway) if not reachable

### `POST /connections/notify`

* notify the platform about changes in a connected Runtime Platform
* body: the base URL of the other Runtime Platform
* output: true or false, depending on whether the other platform responded to `/info` call; if it does not respond, connection is removed
* errors: 404 if the other platform is not connected to this platform

### `DELETE /connections`

* disconnect from another Runtime Platform (both directions)
* body: the base URL of the other Runtime Platform
* output: `true/false` whether it was disconnected
* errors: 502 if not reachable (only if it was connected before)

### Common Themes of different Routes

* some routes have an optional `forward` parameter, telling whether the call can be forwarded to another platform, if the agent or action is not found in a container on this one (default: true)
* typically, the API will return HTTP Status codes 502 if the call could not be forwarded to the target container or platform, and 404 if the agent or action is question has not been found (an exception being the `DELETE` routes, since here the effect is the same whether the container/platform was found; those will just return `false` in this case)


## Models

### RuntimePlatform
```
{
    "baseUrl": URL,
    "containers": [ AgentContainer ],
    "provides": [ string ],
    "connections": [ URL ]
}
```

### AgentContainer

```
{
    "containerId": string,
    "image": AgentContainerImage,
    "agents": [ AgentDescription ],
    "runningSince": "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    "connectivity": {
        "publicUrl": URL,
        "apiPortMapping": int,
        "extraPortMappings": {
            int: {
                "protocol": string,
                "description": string
            }
        }
    }
}
```

### AgentContainerImage
```
{
    "imageName": string,
    "requires": [ string ],
    "provides": [ string ],
    "name": string,
    "description": string,
    "provider": string
    "apiPort": int, // default: 8082
    "extraPorts": {
        int: {
            "protocol": string,
            "description": string
        }
    }
}
```

### AgentDescription

```
{
    "agentId": string,
    "agentType": string,
    "actions": [ Action ]
}
```

### Action
```
{
    "name": string,
    "parameters": {string: string},
    "result": string
}
```

### Message
```
{
    "payload": any,
    "replyTo": string
}
```

The relations between the model classes used in the different API routes are depicted in the following figure:

![Model Classes](img/models.png)

Note that this is not a 100% accurate reproduction of the classes in `jiacpp-model`, e.g. the `port` is actually not an attribute of the PortDescription but a key in a hash map.
