# Requirements and Provisions

Agent Container images can have *requirements* that have to be fulfilled by a *provision* of either another Agent Container, or by the Platform itself. If an image's requirements are not fulfilled, starting the container will yield an error.

## Provisions Format

The platform's provisions are collected from different sources:

* Certain values in the platform's configuration, e.g. whether it provides authentication, or whether containers are running in Docker or Kubernetes. Such provisions are in the form `config:<key>=<value>`.
* One provision is created for each (unique) Agent Container Image, Agent type, and Action available on the Runtime Platform. Those are in the form `image:<image-name>`, `agent:<agent-type>`, and `action:<action-name>` respectively. Each of those is listed only once, if multiple of the same are present.
* Agent Container images can specify additional provisions in their `provides` attribute, which are also added to the Runtime Platform's provisions. Those should _not_ include the agents and actions provided by that image, since those are already added by the platform itself.

**Note**: The `requires` attribute will be read from the `image` block in the request when calling the `POST /container` route, whereas the `provides` attribute is read from the `image` retrieved from the `GET /info` route of the running Agent Container. This means that (a) adding a provision in the post-container request will have no effect, and (b) requirements-checking can be circumvented by omitting the requirements in the post-container request.

## Planned Extensions

This feature is still work-in-progress and will be extended in the future. The implicit provisions of the platform will be extended with more values, e.g. for availability of GPU support, or custom user-provided provisions.

At the moment, requirements are just strings that are compared to each others. In the future, this shall be extended to also allow more complex checks, e.g. such that Agent Container images could check whether a certain minimum CUDA version is present.

Also, provisions of connected Runtime Platforms are not yet taken into account. Here, it should be differentiated between provisions that can actually be used by connected platforms (e.g. agents and their actions) and those that can not (e.g. GPU support). Also, currently this would pose problems w.r.t. circular invocations.
