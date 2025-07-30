# Protocols

## Message Flows in Reference Implementation

### Runtime Platform to Agent Container

* **message**: HTTP Request to CA (in HTTP handler thread), send message via `agent_ref tell`
* **broadcast**: HTTP Request to CA (in HTTP handler thread), send message via `broker.publish`
* **invoke**: HTTP Request to CA (in HTTP handler thread), send `Invoke` to agent via `ask invoke` protocol, wait for response (in CA thread), reply in HTTP response

![RP-CA Interactions](img/messages-rp-ca.png)

### Agent Container to Runtime Platform

* **message**: agent sends HTTP Request to RP via helper method in super class
* **broadcast**: agent sends HTTP Request to RP via helper method in super class
* **invoke**: agent sends HTTP Request to RP via helper method in super class, waits for response in its own thread

![CA-RP Interactions](img/messages-ca-rp.png)

### Within Agent Container

* **message**: using regular JIAC-VI `agent_ref tell`
* **broadcast**: using regular JIAC-VI `broker publish`
* **invoke**: using regular JIAC-VI `ask invoke`, either with OPACA `Invoke` object or any other payload

![Internal Interactions](img/messages-internal.png)

### Runtime Platform to Runtime Platform 

* **send**, **broadcast** and **invoke** have optional query parameter `forward` with default value `true`
* look up connected RP that have the target agent or action
* **send** and **broadcast**: forward message to all valid platforms
* **invoke**: forward invoke to first valid connected platform
* `forward` parameter gets automatically set to `false` in the request to the connected platform to prevent endless forwarding

![Platform Connection Protocol](img/messages-rp-rp.png)

## Protocol for connecting two Runtime Platforms

* platform A receives request to connect to platform B
* sends request to connect to platform A (itself) to platform B, adds B to "pending"
* platform B does the same, sending another request back to A and adding A to "pending"
* platform A recognizes the new request as already being processed and replies immediately
* platform B replies to original request, both platforms call "info" on each other

![Platform Connection Protocol](img/connect-platform.png)

## Protocol for notifying about updated Containers or connected Platforms

* container/platform calls `/containers/notify` or `/connections/notify` with own ID/URL respectively
* the idea behind "notify, then pull info" instead of "push info" is to make sure that the info does actually come from the container/platform in question and not someone else
* receiving platform calls `/info` for that container/platform, stores updated information
* return `true` if update successful, `false` if not reachable (see below) and 404 if unknown/not in list
* if container/platform are not reachable, their information is removed from the platform
* can be called if container's agents/actions change, if container is about to die, or at any time by the user
* update in containers (via add/remove or update) automatically triggers notification of connected platforms

![Notify/Update Protocol](img/notify-update.png)
