package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Describes a stream provided by an agent
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class Stream {

    /** name of the stream */
    @NonNull
    String name;

    /** mode of this steam, sending or receiving */
    @NonNull
    Mode mode;

    public enum Mode {
        GET, POST
    }

}

