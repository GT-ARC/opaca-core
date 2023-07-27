package de.dailab.jiacpp.platform.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.dailab.jiacpp.util.RestHelper;
import de.dailab.jiacpp.platform.PlatformConfig;
import de.dailab.jiacpp.platform.PlatformConfig.SessionPolicy;

/**
 * TODO JAVADOCS
 */
@Component
@Log
public class Session {

	@Autowired
	private PlatformConfig config;

    @Autowired
    private SessionData data;

    private static final String filename = "Session.json";
    private Path filePath;

    @PostConstruct
    public void init() {
        // set the file path for storage and load operations
        String userDirectory = System.getProperty("user.dir");
        filePath = Paths.get(userDirectory, filename);

        loadFromFile();

        if (config.sessionPolicy != SessionPolicy.SHUTDOWN) {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(this::saveToFile, 60, 60, TimeUnit.SECONDS);
        }
    }

    private void loadFromFile() {
        if (filePath.toFile().exists()) {
            try {
                String content = Files.readString(filePath);
                SessionData lastdata = RestHelper.readObject(content, SessionData.class);

                this.data.reset();
                this.data.tokens.putAll(lastdata.tokens);
                this.data.runningContainers.putAll(lastdata.runningContainers);
                this.data.connectedPlatforms.putAll(lastdata.connectedPlatforms);
                this.data.dockerContainers.putAll(lastdata.dockerContainers);
                this.data.usedPorts.addAll(lastdata.usedPorts);
                this.data.userCredentials.putAll(lastdata.userCredentials);
    
            } catch (IOException e) {
                log.severe("Could not load Session data: " + e);
            }
        }
    }

    public void saveToFile() {
        try {
            String content = RestHelper.writeJson(this.data);
            Files.writeString(filePath, content);
        } catch (IOException e) {
            log.severe("Could not save Session data: " + e);
        }
    }
}
