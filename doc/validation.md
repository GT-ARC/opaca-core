# Validation

The platform supports validation of the arguments passed to agents via the 
`/invoke/{action}` command against their defined parameter types using 
[JSON Schema](https://json-schema.org/) Draft-07.

## Primitive Types

The parameters for an agent action are defined using the `Parameter` class,
which, among other things, makes it possible to define a type for that parameter.
That type can be any of the following primitive types:

* `string`, a normal string value surrounded by double-quotes, e.g. `"string"`
* `integer`, a whole number, e.g. `42`
* `number`, a floating point number, e.g. `42.0`, but also `42`
* `boolean`, a boolean value, so either `true` or `false`

These correspond directly to their JSON Schema counterparts.
[See here](https://json-schema.org/understanding-json-schema/reference/type) 
for more details and also more examples for allowed values per type.

## Complex Types

The type definition also allows complex types. For defining objects, it is necessary to first set up a JSON Schema type definition
in the container's image JSON file. There are 2 ways to do that:

1. Add a JSON Schema type definition directly into the file as an entry in the
    `definitions` map, which maps the name of the type to a valid JSON Schema type definition.
2. Link to a definition in the `definitionsByUrl` map, which maps the type's name
   to a URL linking to a JSON file with the type definition.

After that, the name of the type (so any key in either of these 2 maps) can 
be used as a type for a parameter.

## Arrays

For Arrays, the `array` type is used. When defining an array as a parameter, 
it is necessary to additionally define the type of the array's `items` using 
the `Parameter.ArrayItems` class. The items' `type` can then be any primitive 
or complex type. It can also be an array, in that case, the item's items' type has to be 
defined again.

## Optional and Mismatched Parameters

The Parameter specification has an (optional) parameter `required: boolean` which specifies if the parameter is required or not. The default is `true`, i.e. unless explicitly specified otherwise, all parameters have to be present and not `null` (i.e. there is no distinction between "required" and "nullable"). Analogously, an action's result type can also have the `required: false` attribute, meaning that the action can return the specified type, or `null`.

A non-required parameter can be omitted entirely from the JSON, or set to `null`, and it is the Agent Container's responsibility to assume a suitable default value. (The container may also use a default if the parameter is omitted, and `null` if it is explicitly set to `null`; if ambiguous, this should be described in the action's `description`.)

For example, consider an action with parameters as `{"foo": {"type": "string"}, "bar": {"type": "boolean", "required": false}}`. Then these arguments will all be **valid**: `{"foo": "x", "bar": true}`, `{"foo": "x", "bar": null}`, `{"foo": "x"}`, whereas the following will be **invalid**: `{"bar": true}` (missing required parameter), `{"foo": null, "bar": true}` (required parameter is null), `{"foo": "x", "bar": 2}` (mismatched type), `{"foo": "x", "buzz": true}` (unknown parameter). Note that in those cases the Platform will actually report the action as "not found".

When an action is called using the `/invoke` route, the OPACA Runtime Platform will search all the available actions of all agents (in one container, in all containers of the platform, or in all containers of this and connected platforms, based on the values of the `containerId` and `forward` query parameters) for an action that matches the given name, agent-id (if set), and parameters, and return "not found" if no such action was found.

This can be confusing, as there is no dedicated error message for "mismatched parameters", and those will just show as "not found", too. If you get a "not found" error when calling an action that you know should be there, please double-check the expected and provided parameter types.

## Examples

For examples, please see `examples/sample-container`.

* The `sample-container.json`file contains sample complex type definitions. 
* The `SampleAgent.kt` file contains sample action and parameter definitions.
