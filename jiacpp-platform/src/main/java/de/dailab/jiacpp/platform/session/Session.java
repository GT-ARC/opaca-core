package de.dailab.jiacpp.platform.session;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.google.common.base.Strings;
import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.model.PostAgentContainer;
import de.dailab.jiacpp.platform.PlatformImpl;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.dailab.jiacpp.util.RestHelper;
import de.dailab.jiacpp.platform.PlatformConfig;
import de.dailab.jiacpp.platform.PlatformConfig.SessionPolicy;

/**
 * Class responsible for Session handling. Load SessionData from JSON file when platform is
 * started, and save it to that file when it is stopped (depending on policy).
 */
@Component
@Log
public class Session {

	@Autowired
	private PlatformConfig config;

    @Autowired
    private SessionData data;

    @Autowired
    private PlatformImpl implementation;


    private static final Path filePath = Paths.get(System.getProperty("user.dir"), "Session.json");

    @PostConstruct
    private void startupPolicy() {
        if (config.sessionPolicy != SessionPolicy.SHUTDOWN) {
            loadFromFile();
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(this::saveToFile, 60, 60, TimeUnit.SECONDS);
        }
        if (config.sessionPolicy == SessionPolicy.SHUTDOWN) {
            startDefaultImages();
        }
        if (config.sessionPolicy == SessionPolicy.RESTART) {
            restartContainers();
        }
        // TODO what about connections? connected-platforms info is restored, but might be outdated
    }

    @PreDestroy
    private void teardownPolicy() throws IOException {
        if (config.sessionPolicy != SessionPolicy.SHUTDOWN) {
            saveToFile();
        }
        if (config.sessionPolicy != SessionPolicy.RECONNECT) {
            stopRunningContainers();
        }
        // TODO possible race condition: session could be saved again after containers are stopped
        //  check again if this method is ALWAYS called; if it is, remove the regular session save
        disconnectPlatforms();
    }

    /*
     * LOAD / SAVE SESSION DATA
     */

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

    private void saveToFile() {
        try {
            String content = RestHelper.writeJson(this.data);
            Files.writeString(filePath, content);
        } catch (IOException e) {
            log.severe("Could not save Session data: " + e);
        }
    }

    /*
     * DEFAULT IMAGES
     */

    public List<File> readDefaultImages() {
        if (Strings.isNullOrEmpty(config.defaultImageDirectory)) return List.of();
        try {
            return Files.list(Path.of(config.defaultImageDirectory))
                    .map(Path::toFile)
                    .filter(f -> f.isFile() && f.getName().toLowerCase().endsWith(".json"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.severe("Failed to read default images: " + e);
            return List.of();
        }
    }

    private void startDefaultImages() {
        log.info("Loading Default Images (if any)...");
        for (File file: readDefaultImages()) {
            log.info("Auto-deploying " + file);
            try {
                // todo: read params?
                var container = new PostAgentContainer();
                container.setImage(RestHelper.mapper.readValue(file, AgentContainerImage.class));
                implementation.addContainer(container);
            } catch (Exception e) {
                log.severe(String.format("Failed to load image specified in file %s: %s", file, e));
            }
        }
    }

    /*
     * STOP AND RESTART CONTAINERS AND CONNECTIONS
     */

    private void restartContainers() {
        log.info("Restarting Last Containers...");
        Map<String, AgentContainer> lastContainers = new HashMap<>(data.runningContainers);
        data.reset();
        for (AgentContainer agentContainer : lastContainers.values()) {
            try {
                // I guess this would require the platform to filter out the "private" params
                // todo: set params
                var container = new PostAgentContainer();
                container.setImage(agentContainer.getImage());
                implementation.addContainer(container);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRunningContainers() throws IOException {
        log.info("Stopping Running Containers...");
        for (AgentContainer container : implementation.getContainers()) {
            try {
                implementation.removeContainer(container.getContainerId());
            } catch (Exception e) {
                log.warning("Exception stopping container " + container.getContainerId() + ": " + e.getMessage());
            }
        }
    }

    private void disconnectPlatforms() throws IOException {
        log.info("Disconnecting from other Platforms...");
        for (String connection : implementation.getConnections()) {
            try {
                implementation.disconnectPlatform(connection);
            } catch (Exception e) {
                log.warning("Exception disconnecting from " + connection + ": " + e.getMessage());
            }
        }
    }
}
