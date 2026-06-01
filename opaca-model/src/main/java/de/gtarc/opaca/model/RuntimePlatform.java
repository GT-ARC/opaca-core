package de.gtarc.opaca.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Description of the Runtime Platform, including deployed Agent Containers.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class RuntimePlatform {

    /** ID of the platform;  */
    @NotNull
    String platformId;

    /** the external base URL where to reach this platform */
    @NotNull
    String baseUrl;

    /** Agent Containers managed by this platform */
    @NotNull @Valid
    List<AgentContainer> containers = List.of();

    /** List of capabilities this platform provides, e.g. "gpu-support"; format to be specified */
    @NotNull
    List<String> provides = List.of();

    /** List of base URLs of other platforms this platform is connected with */
    @NotNull
    List<String> connections = List.of();

    /** when the platform was started */
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "Z")
    ZonedDateTime runningSince;

}
