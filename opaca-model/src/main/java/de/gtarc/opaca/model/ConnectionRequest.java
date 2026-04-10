package de.gtarc.opaca.model;

import lombok.*;

/**
 * Used for the POST /connection route to connect to another platform.
 */
@Data @AllArgsConstructor @NoArgsConstructor
@ToString(exclude = {"token"})
public class ConnectionRequest {

    /** URL of another platform to connect to */
    @NonNull
    String url;

    /** whether to request the other platform to connect back to self */
    boolean connectBack = false;

    /** access token to use in case the other platform requires authentication */
    String token = null;

}
