# OPACA Examples

This module contains some examples for testing the OPACA reference implementation. The examples don't do anything
meaningful, but they showcase different features of the API, the Runtime Platform and the Agent Container, and they
can also serve as a blueprint for creating actual agent containers.

## Sample-Container

The Sample-Container example is used mostly for unit testing, for which it provides different (nonsensical) actions
and also does some bookkeeping on e.g. the last message that was received, so that can be tested, too.

## Ping-Pong

The Ping-Pong example can be used for semi-automated manual testing, in particular inter-platform communication, by
deploying the ping-container on one and the pong-container on another (connected) platform, or just both on the same.

## Testing

Not a real example, but can be used as a testing harness for testing behavior in different situation, e.g. for investigating hard to reproduce bugs. There are several scenarios defined in different modules, all executed by the same `Main` module. They can all be executed without having to build a Docker image and deploying it to a Runtime Platform but just from the IDE (if properly configured) or the command line.
