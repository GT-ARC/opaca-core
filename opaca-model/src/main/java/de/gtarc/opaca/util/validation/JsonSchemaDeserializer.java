package de.gtarc.opaca.util.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.networknt.schema.JsonSchema;

import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import java.io.IOException;
import java.net.URI;

public class JsonSchemaDeserializer extends JsonDeserializer<JsonSchema> {

    @Override
    public JsonSchema deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        return ArgumentValidator.factory.getSchema(node);
    }

}
