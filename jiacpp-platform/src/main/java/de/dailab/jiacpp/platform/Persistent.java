package de.dailab.jiacpp.platform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.FileWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.util.RestHelper;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.containerclient.DockerClient.DockerContainerInfo;

import lombok.Data;

@Component
public class Persistent {

	@Autowired
	PlatformConfig config;

    @Data @Component
    public static class PersistentData {

        /* PlatformImpl variables */
        public Map<String, String> tokens = new HashMap<>();
        public Map<String, AgentContainer> runningContainers = new HashMap<>();
        public Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();
        /* DockerClient variables */
        public Map<String, DockerContainerInfo> dockerContainers = new HashMap<>();
        public Set<Integer> usedPorts = new HashSet<>();
        /* TokensUserDetailsService variables */
        public Map<String, String> userCredentials = new HashMap<>();

        public void reset() {
            this.tokens.clear();
            this.runningContainers.clear();
            this.connectedPlatforms.clear();
            this.dockerContainers.clear();
            this.usedPorts.clear();
            this.userCredentials.clear();
        }
    
        public Map<String, AgentContainer> getRunningContainers() {
            return new HashMap<>(this.runningContainers);
        }
    }


    @Autowired
    PersistentData data;

    private static final String filename = "/home/benjamin/Desktop/persistent.json";

    private transient ScheduledExecutorService scheduler;


    @PostConstruct
    public void init() {
        loadFromFile();
        this.scheduler = Executors.newScheduledThreadPool(1);
        if (!config.stopPolicy.equals("stop")) {
            this.startPeriodicSave();
        }
    }

    private void loadFromFile() {
        File file = new File(filename);
        if (file.exists()) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
                PersistentData lastdata = RestHelper.readObject(content, PersistentData.class);
                this.data.tokens = lastdata.tokens;
                this.data.runningContainers = lastdata.runningContainers;
                this.data.connectedPlatforms = lastdata.connectedPlatforms;
                this.data.dockerContainers = lastdata.dockerContainers;
                this.data.usedPorts = lastdata.usedPorts;
                this.data.userCredentials = lastdata.userCredentials;
    
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void startPeriodicSave() {
        final Runnable saver = new Runnable() {
            public void run() { 
                saveToFile(); 
            }
        };
        scheduler.scheduleAtFixedRate(saver, 60, 60, TimeUnit.SECONDS);
    }

    private void saveToFile() {
        System.out.println("SAVE TO FILE");
        System.out.println(this.data);
        try {
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, (JsonSerializer<ZonedDateTime>) (date, type, jsonSerializationContext) -> new JsonPrimitive(date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .setPrettyPrinting()
                .create();
            FileWriter writer = new FileWriter(filename);
            gson.toJson(this.data, writer);
            writer.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
    }
}
