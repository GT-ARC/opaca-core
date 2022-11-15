package de.dailab.jiacpp.plattform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Settings for the Runtime Platform. This is not a part of the JIAC++ model since
 * most of these settings are platform specific and might be different for different
 * implementations of the Runtime Platform, e.g. using Docker vs. Kubernetes.
 */
@Configuration
public class PlatformConfig {

    @Value("${server.port}")
    String serverPort;

    @Value("${public_url}")
    String publicUrl;

    @Value("${container_timeout_sec}")
    Integer containerTimeoutSec;


    // TODO
    //  docker registries and logins
    //  (remote) docker host
    //  auth stuff for platform itself? tbd
    //  GPU support and other "features" of this specific platform

    // TODO or define "ContainerClient" similar to JES with different implementations for
    //  Docker and Kubernetes, each with its own settings?

}
