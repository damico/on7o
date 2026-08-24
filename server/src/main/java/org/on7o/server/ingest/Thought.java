package org.on7o.server.ingest;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * An immutable thought event: the content as captured plus the technical metadata
 * around it. Later stages (transcription, interpretation) attach to this record
 * by id and never rewrite it.
 *
 * <p>A thought has one of three origins, told apart by {@code source}:
 * <ul>
 *   <li>{@link #SOURCE_AUDIO} - captured by a device, with audio on disk;</li>
 *   <li>{@link #SOURCE_DERIVED} - derived from a single entity found in another
 *       thought's cThought, carrying {@code parentId} and {@code sourceEntity}
 *       instead of audio;</li>
 *   <li>anything else, {@link #SOURCE_SYNTHETIC} by convention - ingested as
 *       plain text, with no audio at all.</li>
 * </ul>
 * Thoughts without audio leave every audio field at zero or null.
 *
 * @param parentId     id of the thought this one was derived from, or null otherwise
 * @param sourceEntity label of the entity this thought was derived from, or null otherwise
 * @param source       origin of the thought, never null once constructed
 */

public record Thought(
        String id,
        String deviceId,
        Instant capturedAt,
        Instant receivedAt,
        String audioFile,
        long audioBytes,
        long pcmBytes,
        long durationMs,
        int sampleRate,
        int channels,
        int bitsPerSample,
        String remoteAddress,
        String parentId,
        String sourceEntity,
        String source) {

    /** Source of a thought captured as audio by a device. */
    public static final String SOURCE_AUDIO = "audio";

    /** Source of a thought derived from an entity of another thought's cThought. */
    public static final String SOURCE_DERIVED = "derived";

    /** Conventional source of a thought ingested as plain text for development and fixtures. */
    public static final String SOURCE_SYNTHETIC = "synthetic";

    /**
     * Fills in the source for metadata written before the field existed: those
     * thoughts were either audio captures or entity derivations.
     */
    public Thought {
        if (source == null || source.isBlank()) {
            source = parentId != null ? SOURCE_DERIVED : SOURCE_AUDIO;
        }
    }

    /** True when this thought was derived from an entity rather than captured from audio. */
    @JsonIgnore
    public boolean isDerived() {
        return parentId != null;
    }

    /** True when this thought has a captured audio file behind it. */
    @JsonIgnore
    public boolean isAudio() {
        return SOURCE_AUDIO.equals(source);
    }
}
