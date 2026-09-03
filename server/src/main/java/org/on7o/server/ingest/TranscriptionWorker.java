package org.on7o.server.ingest;

import org.on7o.server.stt.Transcriber;
import org.on7o.server.stt.Transcription;
import org.on7o.server.stt.TranscriptionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a stored capture into text and keeps the result beside it.
 *
 * <p>The single reason this class exists is that transcription must not happen
 * on the request thread that ingested the audio. The device gives up on a
 * response after eight seconds, while Whisper on CPU takes several times the
 * length of the audio, so a transcribing ingest reported failure on the device
 * for every capture the server had in fact stored perfectly.
 *
 * <p>The capture is the record and the transcription is derived from it, so
 * deriving it can happen after the caller has been told the thought is safe.
 * Until it finishes, the thought simply has no transcription, which is a state
 * the rest of the system already understands: the index page shows it as
 * pending and offers to transcribe it.
 *
 * <p>A background transcription is best effort. It is not retried, and one
 * still running when the server stops is lost, which costs nothing but a
 * button press: the audio is on disk and
 * {@code GET /api/thoughts/{id}/transcribe/stream} runs it again.
 */
@Service
public class TranscriptionWorker {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionWorker.class);

    private final ThoughtStore store;
    private final Transcriber transcriber;
    private final TranscriptionProperties properties;

    /** Thoughts currently being transcribed in the background. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public TranscriptionWorker(ThoughtStore store,
                               Transcriber transcriber,
                               TranscriptionProperties properties) {
        this.store = store;
        this.transcriber = transcriber;
        this.properties = properties;
    }

    /**
     * Transcribes the thought on a virtual thread and returns at once.
     *
     * <p>A thought already being transcribed in the background is not submitted
     * twice. An explicit request from the user is a different matter and goes
     * through {@link #transcribe(Thought)} regardless: pressing a button means
     * "do this now".
     *
     * @param thought a stored capture with audio behind it
     */
    public void submit(Thought thought) {
        if (!properties.isEnabled()) {
            log.debug("speech-to-text disabled, {} stays untranscribed", thought.id());
            return;
        }
        if (!thought.isAudio()) {
            return;
        }
        if (!inFlight.add(thought.id())) {
            log.debug("{} is already being transcribed", thought.id());
            return;
        }

        Thread.ofVirtual().name("transcribe-" + thought.id()).start(() -> {
            try {
                Transcription transcription = transcribe(thought);
                log.info("transcribed {} ({} chars)", thought.id(), transcription.text().length());
            } catch (IOException | RuntimeException e) {
                // A failing engine must never cost the user a thought. The audio
                // is stored and the transcription can be asked for again.
                log.warn("could not transcribe {}: {}", thought.id(), e.getMessage());
            } finally {
                inFlight.remove(thought.id());
            }
        });
    }

    /**
     * Transcribes the thought on the calling thread and stores the result.
     *
     * @param thought a stored capture with audio behind it
     * @return what was understood
     * @throws org.on7o.server.stt.TranscriptionException when the engine is unreachable or fails
     * @throws IOException            when the result cannot be written
     */
    public Transcription transcribe(Thought thought) throws IOException {
        Path audio = store.audioPath(thought);
        Transcription transcription = transcriber.transcribe(audio, thought.id());
        store.saveTranscription(transcription);
        return transcription;
    }

    /** Whether a background transcription of this thought is still running. */
    public boolean isTranscribing(String thoughtId) {
        return inFlight.contains(thoughtId);
    }
}
