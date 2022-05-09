package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sent to the Agent Container on initialization, to notify them of their own
 * container ID and how to reach their parent Runtime Platform.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class Initialize {

    /** this container's unique container ID, given by the platform (or e.g. Docker) */
    String containerId;

    /** the URL where to find this container's parent runtime platform */
    String platformUrl;

}
