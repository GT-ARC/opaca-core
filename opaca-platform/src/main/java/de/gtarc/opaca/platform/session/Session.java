package de.gtarc.opaca.platform.session;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.google.common.base.Strings;
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.PostAgentContainer;
import de.gtarc.opaca.platform.PlatformImpl;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.gtarc.opaca.util.RestHelper;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.PlatformConfig.SessionPolicy;

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
                this.data.startContainerRequests.putAll(lastdata.startContainerRequests);
                this.data.connectedPlatforms.putAll(lastdata.connectedPlatforms);
                this.data.dockerContainers.putAll(lastdata.dockerContainers);
                this.data.usedPorts.addAll(lastdata.usedPorts);
                this.data.users.putAll(lastdata.users);
    
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
        try (Stream<Path> paths = Files.list(Path.of(config.defaultImageDirectory))) {
            return paths
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
                var container = RestHelper.mapper.readValue(file, PostAgentContainer.class);
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
        List<PostAgentContainer> startedContainers = List.copyOf(data.startContainerRequests.values());
        data.reset();
        for (PostAgentContainer postContainer : startedContainers) {
            try {
                implementation.addContainer(postContainer);
            } catch (IOException e) {
                log.warning("Exception restarting container: " + e.getMessage());
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
