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

import de.gtarc.opaca.model.ConnectionRequest;
import de.gtarc.opaca.platform.services.ConnectionsService;
import de.gtarc.opaca.platform.services.ContainersService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.google.common.base.Strings;
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.PostAgentContainer;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.gtarc.opaca.util.RestHelper;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.PlatformConfig.SessionPolicy;

/**
 * Class responsible for Session handling. Load SessionData from a JSON file when the platform is
 * started and save it to that file when it is stopped (depending on policy).
 */
@Component
@Log4j2
public class Session {

	@Autowired
	private PlatformConfig config;

    @Autowired
    private SessionData data;

    // TODO cyclic autowires... is this a problem?
    //  certainly not pretty... split up this class and distribute to the individual services?
    //  problem: preDestroy of SessionData would have to be executed FIRST, how to ensure this?

    @Autowired
    private ContainersService containersService;

    @Autowired
    private ConnectionsService connectionsService;


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
    private void teardownPolicy() {
        containersService.setIsShuttingDown();
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
                SessionData lastData = RestHelper.readObject(content, SessionData.class);

                this.data.reset();
                this.data.runningContainers.putAll(lastData.runningContainers);
                this.data.startContainerRequests.putAll(lastData.startContainerRequests);
                this.data.connectedPlatforms.putAll(lastData.connectedPlatforms);
                this.data.dockerContainers.putAll(lastData.dockerContainers);
                this.data.usedPorts.addAll(lastData.usedPorts);
    
            } catch (IOException e) {
                log.error("Could not load Session data", e);
            }
        }
    }

    private void saveToFile() {
        try {
            String content = RestHelper.writeJson(this.data);
            Files.writeString(filePath, content);
        } catch (IOException e) {
            log.error("Could not save Session data", e);
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
            log.error("Failed to read default images: {}", e.getMessage());
            return List.of();
        }
    }

    private void startDefaultImages() {
        log.info("Loading Default Images (if any)...");
        for (File file: readDefaultImages()) {
            log.info("Auto-deploying {}", file);
            try {
                var container = RestHelper.mapper.readValue(file, PostAgentContainer.class);
                containersService.addContainer(container, -1); // TODO restore user token
            } catch (Exception e) {
                log.error("Failed to load image specified in file {}: {}", file, e);
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
                containersService.addContainer(postContainer, -1); // TODO restore user token
            } catch (IOException e) {
                log.warn("Exception restarting container: {}", e.getMessage());
            }
        }
    }

    private void stopRunningContainers() {
        log.info("Stopping Running Containers...");
        for (AgentContainer container : containersService.getContainers()) {
            try {
                containersService.removeContainer(container.getContainerId());
            } catch (Exception e) {
                log.warn("Exception stopping container {}: {}", container.getContainerId(), e.getMessage());
            }
        }
    }

    private void disconnectPlatforms() {
        log.info("Disconnecting from other Platforms...");
        for (String url : connectionsService.getConnections()) {
            try {
                connectionsService.disconnectPlatform(new ConnectionRequest(url, false, null));
            } catch (Exception e) {
                log.warn("Exception disconnecting from {}: {}", url, e.getMessage());
            }
        }
    }
}
