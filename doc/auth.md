# Authentication

The Runtime Platform can optionally require Authentication on all routes, as determined by the `ENABLE_AUTH` environment variable. Authentication is handled through a JWT (JSON Web Token) bearer token, which is issued by the Runtime Platform to all authorized users as well as to all Agent Containers started by the platform.

Using the Swagger UI, you have to click the "Authorize" button and enter the token, which will subsequently be used for all requests. When calling the routes programmatically, including e.g. from within the Agent Container, the token has to be provided as a header field, e.g. `connection.setRequestProperty("Authorization", "Bearer " + token)`.

Note: At the moment the Runtime Platform is using a very basic configuration for authentication, allowing just a single authorized user. Later, this will be extended to e.g. a passwords-file based authentication, or relying on an external User Management service.

## Authenticating Users against the Runtime Platform

The Runtime Platform utilizes O2Auth as its authentication mechanism, requiring users to log in using their credentials. These credentials are subsequently compared to those provided in the environmental variables, as outlined in the previous section. Upon successful validation, the user is issued a token which enables interaction with the Runtime Platform. To facilitate this interaction, the user must inject the JWT into the lock, visibly located in the upper right corner of the window when accessing the Swagger UI. Subsequently, the JWT is consistently included in the header of all requests sent by the endpoints.

## Authenticating Agent Containers against the Runtime Platform

When an AgentContainer is initiated, it is assigned its own token in the `TOKEN` environmental variable. This token serves as an authentication mechanism when communicating with the Runtime Platform and has to be provided as a header in all requests (see above). In the JIAC VI Reference Implementation, this is handled automatically by the `ContainerAgent` and `AbstractContainerizedAgent`.

## Authenticating the Runtime Platform against its Agent Containers

If authentication is enabled, the RuntimePlatform sends the AgentContainer's own token with each request to the container, so it can verify that the requests actually came from the RuntimePlatform and not from some external entity, bypassing the platform.

## Authenticating the Runtime Platform against another Runtime Platform

If authentication is enabled at a remote RuntimePlatform, the `POST /connections` route requires additional parameters `username` and `password` which are used to log in at the other platform and acquire an access token. This token is then associated with the platform and used in all following requests to that platform, e.g. for invoking actions there.
