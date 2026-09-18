# Websocket Events

Besides the [OPACA API](api.md), the Runtime Platform also provides a Websocket under the `/subscribe` route. By connecting to that websocket, clients can subscribe to different events (the same Events that can be retrieved using the API's `/history` route), which are generated whenever a route is called on the OPACA platform or on other occasions. This can be used e.g. by external tools to monitor service invocations, or to get notified about containers being added to or removed from the platform.

After connecting to the `/subscribe` endpoint, the client is expected to send a single string, being the type of events to subscribe to. Please refer to the [WebSocketConnector.java](../opaca-model/src/main/java/de/gtarc/opaca/util/WebSocketConnector.java) for a reference client implementation.

## API Events

An `ApiEvent` is generated whenever one of the OPACA API Routes are called, e.g. for adding a container, or invoking an action. API Events are only generated on _modifying_ routes, i.e. not on purely informational routes like "get-containers". Each API call may result in several events, mirroring the different phases of the call, i.e. `CALL` (calling the route at the runtime platform), `FORWARD` (the call being forwarded to an agent container), `SUCCESS` and `ERROR` (the result of the call). API Events will include the HTTP method and the full `route` (including e.g. any path- or query-parameters) but not the payload the routes were called with.

Only events of phase `SUCCESS` will be sent to the websocket. To subscribe to those events, send `api/<route>` after connecting to the `/subscribe` websocket, where `<route>` is the first path-segment of the API route, e.g. `invoke` for all action invocations, or `/containers` to receive updates on Agent Containers being added to or removed from the platform (although those can better be monitored using Platform Events, see below).

While this is most useful for e.g. external monitoring tools, an AgentContainer can also subscribe to those events. The JIAC VI reference implementation will do so when setting the respective parameter to `true` in the `ContainerAgent`.

## Platform Events

<!-- TODO not sure yet if I will split those up into two types yet, update then -->


## Broadcast Events

Whenever the `/broadcast` route is called, an analogous `BroadcastEvent` is generated, allowing outside observers and websocket clients to react to those broadcast messages. In order to subscribe to a broadcast channel, send `broadcast/<channel>` after connecting to the `/subscribe` websocket. You can also send just `broadcast/` to subscribe to broadcast messages on all channels. The events will include the usual ID, timestamp and type, as well as the full channel and original broadcast message. 


## Events Model

<!-- TODO -->
