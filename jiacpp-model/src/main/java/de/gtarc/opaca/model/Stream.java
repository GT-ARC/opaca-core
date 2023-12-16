package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a stream provided by an agent
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class Stream {

    /** name of the stream */
    String name;

    /** mode of this steam, sending or receiving */
    Mode mode;

    public enum Mode {
        GET, POST
    }

}

