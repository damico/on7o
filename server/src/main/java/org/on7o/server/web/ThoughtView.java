package org.on7o.server.web;

import org.on7o.server.ingest.Thought;
import org.on7o.server.stt.Transcription;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * View model that combines a {@link Thought} with its optional {@link Transcription}
 * and interpretation stage flags for rendering in the index template.
 */
public final class ThoughtView {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                             .withZone(ZoneId.of("UTC"));

    private final Thought thought;
    private final Optional<Transcription> transcription;
    private final Optional<String> rawThought;
    private final boolean hasQuestions;
    private final boolean hasCThought;

    public ThoughtView(Thought thought,
                       Optional<Transcription> transcription,
                       Optional<String> rawThought,
                       boolean hasQuestions,
                       boolean hasCThought) {
        this.thought = thought;
        this.transcription = transcription;
        this.rawThought = rawThought;
        this.hasQuestions = hasQuestions;
        this.hasCThought = hasCThought;
    }

    /** Thought id. */
    public String getId() {
        return thought.id();
    }

    /** Captured timestamp formatted for display (UTC). */
    public String getCapturedAt() {
        return DISPLAY_FORMAT.format(thought.capturedAt());
    }

    /** Audio duration formatted as m:ss. */
    public String getDuration() {
        long totalSeconds = thought.durationMs() / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /** Device that recorded this thought. */
    public String getDeviceId() {
        return thought.deviceId() != null ? thought.deviceId() : "unknown";
    }

    /** True when this thought was derived from an entity rather than captured from audio. */
    public boolean isDerived() {
        return thought.isDerived();
    }

    /** Label of the entity this thought was derived from, or null when captured from audio. */
    public String getSourceEntity() {
        return thought.sourceEntity();
    }

    /** Id of the thought this one was derived from, or null when captured from audio. */
    public String getParentId() {
        return thought.parentId();
    }

    /** Transcription text, or null when no transcription exists. */
    public String getText() {
        return transcription.map(Transcription::text).orElse(null);
    }

    /** True when no transcription is available. Never true for a derived thought. */
    public boolean isPending() {
        return !isDerived() && transcription.isEmpty();
    }

    /** Raw ontology Turtle (stage 1 output), or null when not yet generated. */
    public String getRawThought() {
        return rawThought.orElse(null);
    }

    /** True when rThought (stage 1) has been generated. */
    public boolean isHasRThought() {
        return rawThought.isPresent();
    }

    /** True when qThought questions (stage 2) are ready for the user. */
    public boolean isHasQuestions() {
        return hasQuestions;
    }

    /** True when cThought (stage 3) has been consolidated. */
    public boolean isHasCThought() {
        return hasCThought;
    }

    /**
     * True when "rThought" should be shown:
     * transcription exists, rThought (stage 1) does not yet.
     * Never true for a derived thought, which has no rThought stage at all.
     */
    public boolean isReadyForRThought() {
        return !isDerived() && !isPending() && rawThought.isEmpty();
    }

    /**
     * True when "Understand" should be shown:
     * rThought (stage 1) exists, questions (stage 2) do not yet.
     */
    public boolean isReadyToUnderstand() {
        return rawThought.isPresent() && !hasQuestions;
    }
}
