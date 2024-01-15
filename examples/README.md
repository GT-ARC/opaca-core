# OPACA Examples

This module contains some examples for testing the OPACA reference implementation. The examples don't do anything
meaningful, but they showcase different features of the API, the Runtime Platform and the Agent Container, and they
can also serve as a blueprint for creating actual agent containers.

## Sample-Container

The Sample-Container example is used mostly for unit testing, for which it provides different (nonsensical) actions
and also does some book-keeping on e.g. the last message that was received, so that can be tested, too.

### Changelog

The sample-container is deployed to <https://gitlab.dai-labor.de/pub/unit-tests/container_registry> in different
versions that are used in unit-tests.

* v12: action timeouts
* v11: added port to test UDP
* v10: check token on incoming calls
* v9: sample action that fails, handle action failure in container agent
* v8: container-agent auto-notifies parent platform when things change
* v7: send token to parent platform
* v6: add container-id to sample-agent's get-info

## Ping-Pong

The Ping-Pong example can be used for semi-automated manual testing, in particular inter-platform communication, by
deploying the ping-container on one and the pong-container on another (connected) platform, or just both on the same.
