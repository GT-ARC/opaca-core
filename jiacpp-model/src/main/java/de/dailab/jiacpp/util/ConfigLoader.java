package de.dailab.jiacpp.util;

import de.dailab.jiacpp.model.AgentContainerImage;

import java.io.IOException;
import java.io.InputStream;

public class ConfigLoader {

    /**
     * Read AgentContainerImage description from JSON file.
     * Path should be absolute path in src/main/resources directory.
     */
    public static AgentContainerImage loadContainerImageFromResources(String path) throws IOException {
        InputStream is = ConfigLoader.class.getResourceAsStream(path);
        return RestHelper.mapper.readValue(is, AgentContainerImage.class);
    }

}
