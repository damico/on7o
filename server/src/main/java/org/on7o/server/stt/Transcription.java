package org.on7o.server.stt;

import java.time.Instant;

/**
 * What the speech-to-text engine understood from a capture, at one point in time.
 *
 * <p>Kept separate from the {@code Thought} it came from: the original audio is
 * the record, and a transcription is one interpretation of it. A better model
 * later produces a new transcription without touching what was captured.
 */
public record Transcription(
        String thoughtId,
        String text,
        String language,
        String engine,
        String model,
        Instant transcribedAt,
        long latencyMs) {
}
