package de.dailab.jiacpp.platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.containerclient.DockerClient.DockerContainerInfo;

@Component
public class Persistent implements Serializable {
    /* PlatformImpl variables */
    public Map<String, String> tokens = new HashMap<>();
    public Map<String, AgentContainer> runningContainers = new HashMap<>();
    public Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();
    public Set<String> pendingConnections = new HashSet<>();
    /* DockerClient variables */
    public Map<String, DockerContainerInfo> dockerContainers = new HashMap<>();
    public Set<Integer> usedPorts = new HashSet<>();
    /* TokensUserDetailsService variables */
    public Map<String, String> userCredentials = new HashMap<>();

    private static final long serialVersionUID = 1L;

    private static final String filename = "/home/benjamin/Desktop/persistent.ser";

    private transient ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        Persistent loaded = loadFromFile();
        if (loaded != null) {
            this.tokens = loaded.tokens;
            this.runningContainers = runningContainers;
            this.connectedPlatforms = connectedPlatforms;
            this.pendingConnections = pendingConnections;
            this.dockerContainers = dockerContainers;
            this.usedPorts = usedPorts;
            this.userCredentials = userCredentials;
        }
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.startPeriodicSave();
    }

    private void startPeriodicSave() {
        final Runnable saver = new Runnable() {
            public void run() { 
                saveToFile(); 
            }
        };
        scheduler.scheduleAtFixedRate(saver, 60, 60, TimeUnit.SECONDS);
    }

    // Serialization method
    private void saveToFile() {
        try {
            FileOutputStream fileOut = new FileOutputStream(filename);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this);
            out.close();
            fileOut.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    // Deserialization method
    private static Persistent loadFromFile() {
        File file = new File(filename);
        if (!file.exists()) {
            // File does not exist, return a new instance
            return new Persistent();
        }
        Persistent p = null;
        try {
            FileInputStream fileIn = new FileInputStream(file);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            p = (Persistent) in.readObject();
            in.close();
            fileIn.close();
        } catch (IOException i) {
            i.printStackTrace();
            return null;
        } catch (ClassNotFoundException c) {
            System.out.println("Persistent class not found");
            c.printStackTrace();
            return null;
        }
        return p;
    }
    


}
