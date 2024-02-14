# Validation

The platform supports validation of the arguments passed to agents via the 
`/invoke/{action}` command against their defined parameter types using 
[JSON Schema](https://json-schema.org/) Draft-07.

## Primitive Types

The parameters for an agent action are defined using the `Parameter` class,
which, among other things, makes it possible to define a type for that parameter.
That type can be any of the following primitive types:

* `string`, a normal string value surrounded by double-quotes, e.g. `"string"`
* `int`, a whole number, e.g. `42`
* `number`, a floating point number, e.g. `42.0`, but also `42`
* `boolean`, a boolean value, so either `true` or `false`

These correspond directly to their JSON Schema counterparts.
[See here](https://json-schema.org/understanding-json-schema/reference/type) 
for more details and also more examples for allowed values per type.

## Complex Types

The type definition also allows complex types. For defining objects, it is necessary to first set up a JSON Schema type definition
in the container's image JSON file. There are 2 ways to do that:

1.  Add a JSON Schema type definition directly into the file as an entry in the
    `definitions` map, which maps the name of the type to a valid JSON Schema type definition.
2. Link to a definition in the `definitionsByUrl` map, which maps the type's name
   to a URL linking to a JSON file with the type definition.

After that, the name of the type (so any key in either of these 2 maps) can 
be used as a type for a parameter.

## Arrays

For Arrays, the `array` type is used. When defining an array as a parameter, 
it is necessary to additionally define the type of the array's items using 
the `Parameter.ArrayItems` class. The `type` can then be any primitive 
or complex type. It can also be an array, in that case, the item type has to be 
defined again.

## Examples

For examples, please see `examples/sample-container`. 
* The `sample-container.json`file contains sample complex type definitions. 
* The `SampleAgent.kt` file contains sample action and parameter definitions.
