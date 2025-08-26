# CHANGELOG

## 0.3 Snapshot

* check container requirements against platform's provisions
* fixed bug in port-checking logic when running in Docker
* various smaller fixes
* reworked `/connection` routes to allow for both uni- and bidirectional connections with and without auth
* using websockets for updates on running containers of connected platforms
* improved/fixed how the current user is checked to avoid possible concurrency issues


## 0.2 Release

* renamed to 'OPACA' and released on GitHub
* added routes to get and post streams
* basic user management
* description and validation of action parameters using JSON Schema
* added route/parameter to get agents of connected platforms, too
* added Open-API compliant description of all agents' actions
* various smaller fixes
* added websocket connection to get notified about platform events


## 0.1 Release

* API and model
* first implementation prototype as 'JIAC++'
* deployment on Docker and Kubernetes
* basic token-based authentication (just one user)
* basic session-persistence
