package de.dailab.jiacpp.plattform.containerclient;

import de.dailab.jiacpp.plattform.PlatformConfig;
import de.dailab.jiacpp.util.RestHelper;

import java.io.IOException;
import java.util.NoSuchElementException;

public interface ContainerClient {

    void initialize(PlatformConfig config);

    void startContainer(String containerId, String imageName) throws IOException, NoSuchElementException;

    void stopContainer(String containerId) throws IOException;

    RestHelper getClient(String containerId);

}
