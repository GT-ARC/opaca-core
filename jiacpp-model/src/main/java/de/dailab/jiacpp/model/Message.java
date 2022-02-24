package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message to be sent to an agent or channel.
 */
@Data @AllArgsConstructor  @NoArgsConstructor
public class Message {

    // TODO receipient here, or in API method, or both?
    // TODO specify format/API for reply-to callback
    // TODO expected result type?

    /** the actual payload of the message */
    Object payload;

    /** URL of REST service where to post replies; optional; for inter/intra platform
     * communication this could also be a "path" like "platformId/containerId/agentId" */
    String replyTo;

}
