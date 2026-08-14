package org.on7o.server.api;

import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.stt.Transcriber;
import org.on7o.server.stt.Transcription;
import org.on7o.server.stt.TranscriptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Triggers on-demand speech-to-text for a captured thought and streams
 * progress back to the client via Server-Sent Events.
 *
 * <p>A single GET opens the stream and starts transcription in a virtual
 * thread. Events emitted:
 * <ul>
 *   <li>{@code status} - "running" when the engine accepts the request</li>
 *   <li>{@code result} - the transcribed text on success</li>
 *   <li>{@code error}  - a message string on failure</li>
 * </ul>
 */
@RestController
public class TranscribeController {

    private static final Logger log = LoggerFactory.getLogger(TranscribeController.class);
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ThoughtStore store;
    private final Transcriber transcriber;

    public TranscribeController(ThoughtStore store, Transcriber transcriber) {
        this.store = store;
        this.transcriber = transcriber;
    }

    /**
     * Opens an SSE stream and immediately starts transcription in a virtual thread.
     * The connection stays open until transcription completes or fails.
     *
     * @param id the thought id to transcribe
     * @return an SSE emitter that will push status, result, or error events
     */
    @GetMapping(value = "/api/thoughts/{id}/transcribe/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Optional<Thought> opt = store.find(id);
        if (opt.isEmpty()) {
            sendEvent(emitter, "error", "thought not found: " + id);
            emitter.complete();
            return emitter;
        }

        Thought thought = opt.get();
        Path audio = store.audioPath(thought);

        Thread.ofVirtual().start(() -> {
            try {
                sendEvent(emitter, "status", "running");

                Transcription t = transcriber.transcribe(audio, id);
                store.saveTranscription(t);

                sendEvent(emitter, "result", t.text());
                emitter.complete();

            } catch (TranscriptionException e) {
                log.warn("on-demand transcription failed for {}: {}", id, e.getMessage());
                sendEvent(emitter, "error", e.getMessage());
                emitter.complete();
            } catch (IOException e) {
                log.error("could not save transcription for {}", id, e);
                sendEvent(emitter, "error", "failed to save transcription");
                emitter.complete();
            }
        });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.debug("SSE client disconnected for event {}", name);
        }
    }
}
