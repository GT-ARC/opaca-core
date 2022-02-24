package de.dailab.jiacpp.api;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.model.RuntimePlatform;

import java.util.List;
import java.util.Map;

public interface RuntimePlatformApi {

    RuntimePlatform getInfo();

    String addContainer(AgentContainerImage container);

    Map<String, AgentContainer> getContainers();

    AgentContainer getContainer(String containerId);

    boolean removeContainer(String containerId);

    boolean connectPlatform(String url);

    List<String> getConnections();

    boolean disconnectPlatform(String url);

}
