package de.gtarc.opaca.model;

import lombok.*;

/**
 * Used for the POST /connection route to connect to another platform.
 */
@Data @AllArgsConstructor @NoArgsConstructor
@ToString(exclude="token")
public class ConnectionRequest {

    /** URL of another platform to connect to */
    @NonNull
    String url;

    /** whether to request the other platform to connect back to self */
    boolean connectBack = false;

    /**
     * Access token for platform to be connected to, if auth required.
     * Currently, this is only needed for the "connect-back" step. Assuming the same Keycloak, the platforms can
     * fetch their info and invoke actions with their own client-accounts, but calling /connect requires elevated
     * permissions. Still this only works if both platforms are using the same keycloak instance, so technically
     * the token could also be the user's own token. This will be changed/extended in the future.
     */
    String token;

}
