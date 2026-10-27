package de.gtarc.opaca.platform.a2a;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.spec.TransportProtocol;

public class SpringMvcRestTransportMetadata implements TransportMetadata {

    @Override
    public String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }
}
