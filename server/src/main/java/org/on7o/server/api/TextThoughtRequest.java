package org.on7o.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.on7o.server.ingest.Thought;

import java.time.OffsetDateTime;

/**
 * Body of a text thought ingestion request.
 *
 * <p>{@code capturedAt} is mandatory and must carry an explicit ISO-8601 offset:
 * a synthetic fixture states when its thought happened, and the projection
 * metrics later depend on that instant being unambiguous.
 *
 * @param text       what the thought says
 * @param capturedAt when the thought happened in the world, with offset
 * @param source     optional origin label, defaulting to {@code synthetic}
 */
public record TextThoughtRequest(

        @NotBlank(message = "text is required")
        @Size(max = MAX_TEXT_LENGTH, message = "text must be at most " + MAX_TEXT_LENGTH + " characters")
        String text,

        @NotNull(message = "capturedAt is required, as an ISO-8601 timestamp with offset")
        OffsetDateTime capturedAt,

        @Size(max = MAX_SOURCE_LENGTH, message = "source must be at most " + MAX_SOURCE_LENGTH + " characters")
        String source) {

    /** Upper bound on a single text thought, generous enough for a long dictation. */
    public static final int MAX_TEXT_LENGTH = 20_000;

    /** Upper bound on the origin label. */
    public static final int MAX_SOURCE_LENGTH = 64;

    /** The text without surrounding whitespace. */
    public String trimmedText() {
        return text == null ? null : text.trim();
    }

    /** The source as given, or {@link Thought#SOURCE_SYNTHETIC} when the caller omitted it. */
    public String sourceOrDefault() {
        return source == null || source.isBlank() ? Thought.SOURCE_SYNTHETIC : source.trim();
    }
}
