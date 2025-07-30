package de.gtarc.opaca.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message to be sent to an agent or channel.
 */
@Data @AllArgsConstructor  @NoArgsConstructor
public class Message {

    /** the actual payload of the message */
    JsonNode payload;

    /** URL of REST service where to post replies; optional; for inter/intra platform
     * communication this could also be a "path" like "platformId/containerId/agentId" */
    String replyTo;

}
