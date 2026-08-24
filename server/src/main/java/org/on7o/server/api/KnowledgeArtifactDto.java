package org.on7o.server.api;

/**
 * One artifact the HCIN can hand out.
 *
 * @param name      file name, which is also how it is fetched
 * @param mediaType what it is served as
 * @param kind      what part it plays
 */
public record KnowledgeArtifactDto(String name, String mediaType, ArtifactKind kind) {

    /** What an artifact is for. */
    public enum ArtifactKind {

        /** The core model: what the terms mean. */
        SCHEMA,

        /** A projection's own vocabulary, layered on the core. */
        VOCABULARY,

        /** SHACL shapes, which say what well-formed data looks like. */
        SHAPES,

        /** Data serialized out of the dataset, rather than a file on disk. */
        DATASET_EXPORT
    }
}
