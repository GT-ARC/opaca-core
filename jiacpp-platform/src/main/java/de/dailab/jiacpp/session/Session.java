package de.dailab.jiacpp.session;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonPrimitive;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.FileWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.dailab.jiacpp.util.RestHelper;
import de.dailab.jiacpp.platform.PlatformConfig;


@Component
public class Session {

	@Autowired
	PlatformConfig config;

    @Autowired
    SessionData data;

    private static final String filename = "Session.json";
    private String filePath;

    private transient ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        // set the file path for storage and load operations
        String currentDirectory = System.getProperty("user.dir");
        filePath = Paths.get(currentDirectory, filename).toString();

        loadFromFile();
        this.scheduler = Executors.newScheduledThreadPool(1);
        if (!config.stopPolicy.equals("stop")) {
            this.startPeriodicSave();
        }
    }

    private void loadFromFile() {
        File file = new File(filePath);
        if (file.exists()) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
                SessionData lastdata = RestHelper.readObject(content, SessionData.class);
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
        try {
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, (JsonSerializer<ZonedDateTime>) (date, type, jsonSerializationContext) -> new JsonPrimitive(date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .setPrettyPrinting()
                .create();
            FileWriter writer = new FileWriter(filePath);
            gson.toJson(this.data, writer);
            writer.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
    }
}
