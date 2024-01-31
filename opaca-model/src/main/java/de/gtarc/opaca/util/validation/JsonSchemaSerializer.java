package de.gtarc.opaca.util.validation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.networknt.schema.JsonSchema;

import java.io.IOException;

public class JsonSchemaSerializer extends JsonSerializer<JsonSchema> {

    @Override
    public void serialize(JsonSchema schema, JsonGenerator generator, SerializerProvider provider) throws IOException {
        var node = schema.getSchemaNode();
        generator.writeObject(node);
    }
}
