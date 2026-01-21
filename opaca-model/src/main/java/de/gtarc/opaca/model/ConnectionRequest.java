package de.gtarc.opaca.model;

import lombok.*;

/**
 * Used for the POST /connection route to connect to another platform.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class ConnectionRequest {

    /** URL of another platform to connect to */
    @NonNull
    String url;

    /** whether to request the other platform to connect back to self */
    boolean connectBack = false;

}
