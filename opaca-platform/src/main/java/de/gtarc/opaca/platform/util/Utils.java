package de.gtarc.opaca.platform.util;

import java.net.URI;

/**
 * Obligatory helper class for "misc" utils methods...
 */
public class Utils {

    /**
     * string payload may or may not be enclosed in quotes -> normalize
     */
    public static String normalizeString(String string) {
        return string.trim().replaceAll("^\"|\"$", "");
    }

    public static void checkUrl(String url) {
        try {
            new URI(url).toURL();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + e.getMessage());
        }
    }
}
