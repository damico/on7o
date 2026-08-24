package org.on7o.server.ingest;

import org.on7o.server.stt.Transcription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

/**
 * Creates thoughts that do not come from captured audio.
 *
 * <p>Acts as a facade over {@link ThoughtStore}: a caller asks for a text
 * thought and gets back one that already carries the {@link Transcription}
 * every downstream interpretation stage expects, without having to know how
 * either artifact is laid out on disk.
 */
@Service
public class ThoughtService {

    private static final Logger log = LoggerFactory.getLogger(ThoughtService.class);

    /** Engine recorded on the transcription of text that was never spoken. */
    public static final String TEXT_ENGINE = "text";

    private final ThoughtStore store;

    public ThoughtService(ThoughtStore store) {
        this.store = store;
    }

    /**
     * Ingests plain text as a thought and writes the matching transcription, so
     * that the rThought, qThought and cThought stages treat it exactly like a
     * capture that has already been through speech-to-text.
     *
     * @param text       what the thought says, already trimmed and non-blank
     * @param capturedAt when the thought happened in the world
     * @param source     origin label, for example {@link Thought#SOURCE_SYNTHETIC}
     * @param address    address the request came from, or null
     * @return the newly created thought
     */
    public Thought ingestText(String text, Instant capturedAt, String source, String address)
            throws IOException {

        Thought thought = store.createTextThought(capturedAt, source, address);
        try {
            // No language detection and no model: the text was never spoken.
            store.saveTranscription(new Transcription(
                    thought.id(), text, null, TEXT_ENGINE, null, thought.receivedAt(), 0L));
        } catch (IOException | RuntimeException e) {
            log.warn("could not save transcription for text thought {}", thought.id(), e);
            throw e;
        }

        log.info("ingested text thought {} ({}, {} chars)", thought.id(), source, text.length());
        return thought;
    }
}
