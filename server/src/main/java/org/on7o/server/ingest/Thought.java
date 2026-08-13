package org.on7o.server.ingest;

import java.time.Instant;

/**
 * An immutable thought event: the audio as captured plus the technical metadata
 * around it. Later stages (transcription, interpretation) attach to this record
 * by id and never rewrite it.
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
        String remoteAddress) {
}
