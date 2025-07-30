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

    /** optional human-readable description of what this stream does */
    String description;

    public enum Mode {
        GET, POST
    }

    public Stream(String name, Mode mode) {
        this(name, mode, null);
    }

}

