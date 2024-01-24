package de.gtarc.opaca.util;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.networknt.schema.JsonSchema;

import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import java.io.IOException;

public class JsonSchemaDeserializer extends JsonDeserializer<JsonSchema> {

    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    @Override
    public JsonSchema deserialize(JsonParser parser, DeserializationContext context) throws IOException, JacksonException {
        JsonNode node = parser.getCodec().readTree(parser);
        return factory.getSchema(node);
    }

}
