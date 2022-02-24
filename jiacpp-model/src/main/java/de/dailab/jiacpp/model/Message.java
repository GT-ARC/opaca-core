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

    Object payload;

    String replyTo;

}
