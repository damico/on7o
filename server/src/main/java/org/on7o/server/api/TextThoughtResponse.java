package org.on7o.server.api;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * What the caller gets back after ingesting a text thought.
 *
 * <p>{@code capturedAt} is echoed with the offset the caller sent, while
 * {@code receivedAt} is the server's own UTC instant. The stored metadata keeps
 * both as UTC instants; the offset here is a courtesy to the caller and carries
 * no extra meaning.
 */
public record TextThoughtResponse(
        String thoughtId,
        String text,
        OffsetDateTime capturedAt,
        Instant receivedAt,
        String source) {
}
