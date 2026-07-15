package de.gtarc.opaca.platform.event;

import org.springframework.context.ApplicationEvent;

public class ContainerChangedEvent extends ApplicationEvent {

    public enum Type {
        ADDED,
        REMOVED,
        UPDATED
    }

    private final String containerId;
    private final Type type;

    public ContainerChangedEvent(Object source, String containerId, Type type) {
        super(source);
        this.containerId = containerId;
        this.type = type;
    }

    public String getContainerId() {
        return containerId;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ContainerChangedEvent{" +
                "containerId='" + containerId + '\'' +
                ", type=" + type +
                '}';
    }
}
