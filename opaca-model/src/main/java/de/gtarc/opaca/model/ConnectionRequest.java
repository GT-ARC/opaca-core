package de.gtarc.opaca.model;

import lombok.*;

/**
 * Used for the POST /connection route to connect to another platform.
 */
@Data @AllArgsConstructor @NoArgsConstructor
//@ToString(exclude = {"clientSecret"})
public class ConnectionRequest {

    /** URL of another platform to connect to */
    @NonNull
    String url;

    /** whether to request the other platform to connect back to self */
    boolean connectBack = false;

    // /** client secret token to use in case the other platform requires authentication */
    // not needed assuming same Keycloak (or no auth at all); more involved when assuming different Keycloak
    // leaving this in for now, just to remind myself how this was, and to not forget e.g. the to-string-exclude
    //String clientSecret = null;

}
