# User-Management

The User-Management system provides the ability to manage multiple users with different authority levels on the same or connected Runtime Platforms. A "User" can either be a real person interacting with the Runtime Platform or a connected Container.

The User-Management is implemented with Spring Boot, Spring Data and uses Spring Security to implement a security filter chain, which checks for required permissions/authorities/roles of the requested user, based on the provided JWT (JSON Web Token). The JWT is generated after a successful login from a user or a successful container addition to the Runtime Platform.

## User Database with MongoDB/Embedded Mongo

The User Management stores all user-related information, including roles and privileges, in a Mongo database. Upon starting the Runtime Platform via the `docker-compose` file, specific environment parameters can be set for the Runtime Platform, pertaining the spring boot configuration to interact with the MongoDB.

There are currently two available options to save user-related information in a MongoDB: An external docker container and an embedded MongoDB. These options can be selected by setting the environment variable `DB_TYPE`. 

### MongoDB Docker Container

Set `DB_TYPE: mongo` to connect to a running MongoDB service. Recommended for production usage.

The configuration for the MongoDB in the spring application is done by two environment variables. The first sets the connection URI and includes the _host_address_, _port_, _authentication_database_, and root _username_ & _password_. The second environment variable sets the name for the user _database_. The following values are the defaults set to each environment variable concerning the connection with the mongo database:

- _uri_: default `mongodb://user:pass@localhost:27017/admin`
- _database_: default `opaca-user-data`

#### Uri Composition

`mongodb://[username]:[password]@[host]:[port]/[authentication_database]`

**NOTE:** \
When starting the Runtime Platform and the MongoDB together, either through the docker-compose file or a modified launch configuration, the application might throw a _MongoSocketReadException_ due to the MongoDB container still starting up. This is a normal behavior and when configured correctly, the application should connect to the database shortly after. If the problem persist, check the connection URI and make sure the MongoDB instance was initialized with the correct root user credentials.

The user-related information is stored in **MongoRepositories**, which is used to create basic CRUD queries to interact with the connected MongoDB. When interacting with the connected MongoDB, the `username` or `name` of the respective entities (user/container) will act as a unique _String_ identifier in the database.

The connected MongoDB is started with two persistent data volumes attached to it. The first volume is called _opaca-platform_data_ and stores all user-related information stored in the `TokenUser` class. The seconds volume is called _opaca-platform_config_ and stores metadata for a sharded cluster. The latter one is currently not actively used, but is defined to prevent randomly generated name associations. These volumes persist, even after the deletion of the respected MongoDB container.

### Embedded MongoDB

Set `DB_TYPE: embedded` to use this saving method. Recommended for quick-starts, development and testing.

The embedded MongoDB is provided by a [third-party dependency](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo.spring). This option uses the same data structure as the external MongoDB container. The currently used MongoDB version is _7.0.4_. Before the first usage, the MongoDB version is downloaded from the official Mongo repository and stored locally.

## User-Management Models

A `TokenUser` consists of a `username`, `password`, `role`, and `privileges`. Further it is assigned a unique ID which is used to store the Object in the In-memory database. This process is done automatically and cannot be modified by any user. 

The username and password are stored as strings, the privileges as a list of strings, since multiple privileges might be assigned to a single user. The password will get encoded before storing it as a string into the database. The role is saved as an enum with the same name. Each user needs to be assigned exactly one role of the pre-defined roles, listed in [Authority Levels](#authority-levels).

### TokenUser
```
{
    "username": string,
    "password": string,
    "role": Role,
    "privileges": [ string ]
}
```

## Authority Levels

When checking a users' authority, their role as well as their privileges are converted to so-called "Granted Authorities". This is primarily used by the Security Filter Chain, but is also checked during requests, which can be accessed by lower-level authorities, but need further checking for specific permissions (e.g. `DELETE /containers` can either be done by an admin or by the contributor who has created the container in question, but **NOT** by a contributor for a container it has not started).

These are the currently implemented Roles:

- **ADMIN**: Has the highest authority and full control over a Runtime Platform. For now, it is also the admin for the user-management system with full access to the user-related routes. There should only be one admin per Runtime Platform/User Database.
- **CONTRIBUTOR**: Is actively contributing to the Runtime Platform by providing and deploying containers. Is able to delete only its own deployed containers. Is not allowed to connect with other Runtime platforms.
- **USER**: Can use the functionalities provided by the running containers on the Runtime Platform. Is also able to send/broadcast messages on the platform and retrieve information about connected platforms or the history of the platform.
- **GUEST**: Is a provisional role with the most limited access. Is only able to get information about the Runtime Platform, running containers and agents.

When a new user is created, it has to be assigned one of the stated roles. There is currently no way to include additional roles.

The roles are part of a role hierarchy, granting the higher role all permissions of the lower role. This is the current role hierarchy:

```
ADMIN > CONTRIBUTOR > USER > GUEST
```

## Authority Overview

In the following table, all implemented routes along with the necessary authority levels are listed. Actions marked with an **X** are executable with the listed authority level in the same column. Specific combinations will include further clarification, when additional authorization is needed inside a single authorization level.

In addition to the following routes, the routes `/login`, `/error`, as well as specific _swagger.io_ paths are permitted to all (non-logged in) users.

A route ending with /** includes every possible path suffix. If no REST methods (GET, DELETE, PUT, POST) are stated, all methods are included. Otherwise, only the given methods are concerned.

|                             | ADMIN | CONTRIBUTOR | USER | GUEST |
|:----------------------------|:-----:|:-----------:|:----:|:-----:|
| /agents/**                  |   X   |      X      |  X   |   X   |
| /broadcast/**               |   X   |      X      |  X   |       |
| /containers/** GET          |   X   |      X      |  X   |   X   |
| /containers/** DELETE/POST  |   X   |     X*      |      |       |
| /connections GET            |   X   |      X      |  X   |       |
| /connections/** DELETE/POST |   X   |             |      |       |
| /history GET                |   X   |      X      |  X   |       |
| /info GET                   |   X   |      X      |  X   |   X   |
| /invoke/**                  |   X   |      X      |  X   |       |
| /send/**                    |   X   |      X      |  X   |       |
| /stream/**                  |   X   |      X      |  X   |       |
| /users GET                  |   X   |             |      |       |
| /users/{username} GET       |   X   |     X**     | X**  |  X**  |
| /users POST                 |   X   |             |      |       |
| /users DELETE               |   X   |             |      |       |
| /users PUT                  |   X   |             |      |       |

*: A contributor can only delete containers which were started by it. \
**: A non-admin can only retrieve user information about itself.

## User Controller

The User Controller provides REST routes to interact with the user management. Currently, the following REST routes have been implemented:

### `GET /users`

Retrieve a list of all users (including containers) that are registered with the connected Runtime Platform. The authority level for this action should only be **ADMIN**.

### `GET /users/{username}`

Get information of a specific user when specifying a username. This can only be used by the **ADMIN** or the **USER** which information are being requested. (**NOTE** that confidential information like the password are obviously not returned)

### `POST /users`

Add a new user into the database. This should only be used by the user-management **ADMIN**, since roles for a new user can get specified.

### `PUT /users/{username}`

Edit user information for a specific user. Since this includes roles/privileges that grant users different authority levels, this is only available for the **ADMIN** role for now.

### `DELETE /users/{username}`

Delete a specific user by the username from the database. This is only allowed by **ADMIN** and the **USER** belonging to the username.
