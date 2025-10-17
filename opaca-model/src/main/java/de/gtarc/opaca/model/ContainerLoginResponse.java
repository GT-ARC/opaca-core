package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds the response on Container-Login, including a status and the container-token.
 * Regular platform-login returns only the token (makes it easier to use, e.g. copy in Swagger).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContainerLoginResponse {

    /** whether the login was successful or not (or could not be tested) */
    LoginStatus status;

    /** container-token, if successful, else null */
    String containerToken;

    public enum LoginStatus {
        /** the container accepted the login and successfully performed a trial login */
        VERIFIED,
        /** the container accepted the login but did not perform a trial login */
        UNVERIFIED,
        /** the container unsuccessfully performed a trial login */
        INVALID,
        /** the container does not support login */
        NOT_SUPPORTED;

        public boolean isOkay() {
            return this == VERIFIED || this == UNVERIFIED;
        }
    }

}
