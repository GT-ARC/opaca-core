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

import org.springframework.stereotype.Component;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.util.RestHelper;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.containerclient.DockerClient.DockerContainerInfo;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

@Component
public class Persistent {

    @Data @NoArgsConstructor @AllArgsConstructor @Component
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

    }

    public PersistentData data;

    private static final String filename = "/home/benjamin/Desktop/persistent.json";

    private transient ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        this.data = loadFromFile();
        System.out.println(this.data);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.startPeriodicSave();
    }

    private PersistentData loadFromFile() {
        PersistentData lastdata = new PersistentData();

        File file = new File(filename);
        if (file.exists()) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
                lastdata = RestHelper.readObject(content, PersistentData.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return lastdata;
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
