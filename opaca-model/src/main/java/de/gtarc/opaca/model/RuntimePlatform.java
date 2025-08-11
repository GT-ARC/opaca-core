package de.gtarc.opaca.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Description of the Runtime Platform, including deployed Agent Containers.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class RuntimePlatform {

    /** ID of the platform;  */
    @NonNull
    String platformId;

    /** the external base URL where to reach this platform */
    @NonNull
    String baseUrl;

    /** Agent Containers managed by this platform */
    @NonNull
    List<AgentContainer> containers = List.of();

    /** List of capabilities this platform provides, e.g. "gpu-support"; format to be specified */
    @NonNull
    List<String> provides = List.of();

    /** List of base URLs of other platforms this platform is connected with */
    @NonNull
    List<String> connections = List.of();

    /** when the platform was started */
    @NonNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "Z")
    ZonedDateTime runningSince;

}
