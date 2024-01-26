# User-Management

The User-Management system provides the ability to manage multiple users with different authority levels on the same or connected Runtime Platforms. A "User" can either be a real person interacting with the Runtime Platform or a connected Container.

The User-Management is implemented with Spring Boot, Spring Data and uses Spring Security to implement a security filter chain, which checks for required permissions/authorities/roles of the requested user, based on the provided JWT (JSON Web Token). The JWT is generated after a successful login from a user or a successful container addition to the Runtime Platform.

## User Database (In-Memory)

Currently, the database used to store all user-related information is the H2 Database, which is a Java based In-Memory database. User information is therefore only available on the current Runtime Platform. The user information consists of a unique ID, the username, a password, one of the pre-defined roles, and a list of privileges.

The user-related information is stored in **JPARepositories**, which is used to create basic CRUD queries to interact with the specified H2 database in the application properties.

## User-Management Models

A `TokenUser` consists of a `username`, `password`, `role`, and `privileges`. Further it is assigned a unique ID which is used to store the Object in the In-memory database. This process is done automatically and cannot be modified by any user. 

The username and password are stored as strings, the privileges as a list of strings, since multiple privileges might be assigned to a single user. The password will get encoded before storing it as a string into the database. The role is saved as an enum with the same name. Each user needs to be assigned exactly one role of the pre-defined roles, listed in [Authority Levels](#authority-levels).

### TokenUser
```
{
    "id": Long,
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
- **USER**: Can use the functionalities provided by the running containers on the Runtime Platform. Is also able to send/broadcast message on the platform and retrieve information about connected platforms or the history of the platform.
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

Delete a specific user by the username from the database. Currently, this is only allowed by **ADMIN** but should also be available to every user when the deletion is about their own user data.
