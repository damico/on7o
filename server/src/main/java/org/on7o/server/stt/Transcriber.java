package org.on7o.server.stt;

import java.nio.file.Path;

/**
 * Turns captured audio into text.
 *
 * <p>Deliberately narrow, so the engine behind it stays replaceable — the README
 * treats speech-to-text as a swappable component.
 */
public interface Transcriber {

    /**
     * @param audio a WAV file on disk
     * @param thoughtId the capture this audio belongs to
     * @return what was understood
     * @throws TranscriptionException when the engine is unreachable or fails
     */
    Transcription transcribe(Path audio, String thoughtId);

    /** Whether the engine is currently reachable. */
    boolean isAvailable();
}
