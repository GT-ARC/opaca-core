# Websockets

Besides the [OPACA API](api.md), the Runtime Platform also provides a Websocket under the `/subscribe` route. By connecting to that websocket, clients can subscribe to different events that are generated whenever a route is called on the OPACA platform. This can be used e.g. by external tools to monitor service invocations, or to get notified about containers being added to or removed from the platform.

After connecting to the `/subscribe` endpoint, the client is expected to send a single string, being the type of events to subscribe to, which is basically the prefix of the REST routes to monitor, e.g. `/containers` to receive updates on Agent Containers being added to or removed from the platform. The Runtime Platform will then send the respective Events to that websocket (the same Events that can be retrieved using the API's `/history` route). Please refer to the [WebSocketConnector.java](../opaca-model/src/main/java/de/gtarc/opaca/util/WebSocketConnector.java) for a reference client implementation.

Note: Only Events with type `SUCCESS` will be sent to the websocket.

While this is most useful for external tools, an AgentContainer can also subscribe to those events. The JIAC VI reference implementation will do so when setting the respective parameter to `true` in the `ContainerAgent`.
