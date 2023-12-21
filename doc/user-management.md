# User-Management

The User-Management system provides the ability to manage multiple users with different authority levels on the same or connected Runtime Platforms. A "User" can either be a real person interacting with the Runtime Platform or a connected Container.

The User-Management is implemented with spring boot, spring data and uses spring security to implement a security filter chain, which checks for required permissions/authorities/roles of the requested user, based on the provided JWT (JSON Web Token). The JWT is generated after a successful login from a user or a successful container addition to the Runtime Platform.

## User Database with MongoDB

The User Management stores all user-related information (_Users_, _Roles_, _Privileges_) in a Mongo database, which has to be running in a separate container alongside the Runtime Platform. Upon starting the Runtime Platform via the `docker-compose` file, specific environment parameters can be set for the Runtime Platform, pertaining the spring boot configuration to interact with the MongoDB.

The configuration for the MongoDB in the spring application is done by various environment variables, which determine the location of the running database, the name of the database as well as the authentication database and the authentication parameters. In total 6 values need to be set to establish a successful connection to the running MongoDB. These include the following values with the given prefix _spring.data.mongodb_:
- _host_: Host address of MongoDB, when application is running in container -> use mongoDB container name, default `localhost`
- _port_: Port of running MongoDB service, default `27017`
- _authentication-database_: default `admin`
- _database_: Name of the database to use, default `jiacpp-user-data`
- _username_: Authentication username, default `user`
- _password_: Authentication password, default `pass`

The user-related information is stored in **MongoRepositories**, which is used to create basic CRUD queries to interact with the connected MongoDB. When interacting with the connected MongoDB, the `username` or `name` of the respective models will act as a unique _String_ identifier in the database.

The connected MongoDB is started with two persistent data volumes attached to it. The first volume is called _jiacpp-platform_data_ and stores all user-related information like `TokenUser`, `Role` or `Privilege`. The seconds volume is called _jiacpp-platform_config_ and stores metadata for a sharded cluster. The latter one is currently not actively used, but is defined to prevent randomly generated name associations. These volumes persist, even after the deletion of the respected MongoDB container.

## User-Management Models

A `TokenUser` consist of [`username`, `password`, `roles`], where the username and password are given by strings, and the roles are a collection, consisting of multiple roles, if necessary. Each single role consists of a unique name and can include multiple privileges, which are made up by only their unique privilege name. The password will get encoded before storing it as a string into the database.

### TokenUser
```
{
    "username": string,
    "password": string,
    "roles": [ Role ]
}
```

### Role
```
{
    "name": string,
    "privileges": [ Privilege ]
}
```

### Privilege
```
{
    "name": string,
}
```

## Authority Levels

When checking a users' authority, their roles as well as their privileges are converted to so-called "Granted Authorities". This is primarily used by the Security Filter Chain, but is also checked during requests, which can be accessed by lower-level authorities, but need further checking for specific permissions (e.g. `DELETE /containers` can either be done by an admin or by the contributor who has created the container in question, but **NOT** by a contributor for a container it has not started).

These are the currently implemented Roles:

- **ADMIN**: Has the highest authority and full control over a Runtime Platform. For now, it is also the admin for the user-management system with full access to the user-related routes. There should only be one admin per Runtime Platform/User Database.
- **CONTRIBUTOR**: Is actively contributing to the Runtime Platform by providing and deploying containers. Is able to delete only its own deployed containers. Is not allowed to connect with other Runtime platforms.
- **USER**: Can use the functionalities provided by the running containers on the Runtime Platform. Is also able to send/broadcast message on the platform and retrieve information about connected platforms or the history of the platform.
- **GUEST**: Is a provisional role with the most limited access. Is only able to get information about the Runtime Platform, running containers and agents.

Each of these roles can include multiple privileges, which have not been used for now.

The roles are part of a role hierarchy, granting the higher role all permissions of the lower role. This is the current role hierarchy:

```
ADMIN > CONTRIBUTOR > USER > GUEST
```

## User Controller

The User Controller provides REST routes to interact with the user management. Currently, the following REST routes have been implemented:

### `GET /users`

Retrieve a list of all users (including containers) that are registered with the connected Runtime Platform. The authority level for this action should only be **ADMIN**.

### `GET /users/{username}`

Get information of a specific user when specifying a username. This can only be used by the **ADMIN** or the **USER** which information are being requested. (**NOTE** that confidential information like the password are obviously not returned)

### `POST /users`

Add a new user into the database. This should only be used by the user-management **ADMIN**, since roles for a new user can get specified.

### `PUT /users/{username}`

Edit user information for a specific user. Since this includes roles/privileges that grant users different authority levels, this is only available for the **ADMIN** role for now. (In the future, this should also be available for the **USER** for changing the username or the password)

### `DELETE /users/{username}`

Delete a specific user by the username from the database. This is only allowed by **ADMIN** and the **USER** belonging to the username.
