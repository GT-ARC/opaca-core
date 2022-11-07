# API and Model

* [x] API routes and models for basic info routes, messages, actions
* [ ] exact format for service parameters, message payload?
  * for now, just using string to show type and assuming that the right type is passed...
  * later, might add data models as JSON schema or some other format
* [ ] finalize REST routes for API
* [ ] security, authentication, etc.

# Runtime Platform

* [x] implement REST API
* [x] start and stop docker containers
* [x] forward API calls to agent containers
* [ ] connect to other runtime platforms

# Agent Container (JIAC VI Ref Impl)

* [x] implement API routes
  * pretty rudimentary; is there a better way to do this, without big imports?
* [x] register agents and their actions on startup
* [x] container agent forwarding API calls to actual agents in the container
* [x] outgoing communication to runtime platform
* [x] make all this a reusable library
