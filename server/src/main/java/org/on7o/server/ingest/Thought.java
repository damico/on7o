package org.on7o.server.ingest;

import java.time.Instant;

/**
 * An immutable thought event: the audio as captured plus the technical metadata
 * around it. Later stages (transcription, interpretation) attach to this record
 * by id and never rewrite it.
 *
 * <p>A thought may instead be derived from a single entity found in another
 * thought's cThought, rather than from captured audio. Such thoughts carry no
 * audio (all audio fields are zero or null) and set {@code parentId} and
 * {@code sourceEntity} instead.
 *
 * @param parentId     id of the thought this one was derived from, or null when captured from audio
 * @param sourceEntity label of the entity this thought was derived from, or null when captured from audio
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
        String sourceEntity) {

    /** True when this thought was derived from an entity rather than captured from audio. */
    public boolean isDerived() {
        return parentId != null;
    }
}
