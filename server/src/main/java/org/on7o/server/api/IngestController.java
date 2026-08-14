package org.on7o.server.api;

import jakarta.servlet.http.HttpServletRequest;
import org.on7o.server.ingest.PcmFormat;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.stt.Transcriber;
import org.on7o.server.stt.Transcription;
import org.on7o.server.stt.TranscriptionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Audio ingestion. The device POSTs the captured bytes as the raw request body —
 * no multipart, no envelope — so the firmware only has to write a fixed header
 * and then stream what the microphone produces.
 *
 * <pre>
 * POST /api/thoughts/audio?device=m5&amp;sampleRate=16000&amp;channels=1&amp;bits=16
 * Content-Type: application/octet-stream
 * Transfer-Encoding: chunked
 * </pre>
 *
 * Chunked bodies are accepted, so the device may start uploading before it knows
 * how long the user will keep the button pressed.
 *
 * <p><b>Transcription is synchronous for now</b>, which is fine for testing but
 * holds the request open for as long as Whisper takes — several times the length
 * of the audio on CPU. Moving it to a worker is the obvious next step.
 */
@RestController
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final ThoughtStore store;
    private final Transcriber transcriber;
    private final TranscriptionProperties sttProperties;

    public IngestController(ThoughtStore store,
                            Transcriber transcriber,
                            TranscriptionProperties sttProperties) {
        this.store = store;
        this.transcriber = transcriber;
        this.sttProperties = sttProperties;
    }

    /** What the caller gets back: the capture, and what was understood from it. */
    public record IngestResult(Thought thought, Transcription transcription) {
    }

    @PostMapping("/api/thoughts/audio")
    public ResponseEntity<IngestResult> ingest(
            @RequestParam(defaultValue = "unknown") String device,
            @RequestParam(defaultValue = "16000") int sampleRate,
            @RequestParam(defaultValue = "1") int channels,
            @RequestParam(defaultValue = "16") int bits,
            @RequestParam(defaultValue = "pcm") String format,
            @RequestParam(required = false) String capturedAt,
            HttpServletRequest request) throws IOException {

        PcmFormat pcmFormat = new PcmFormat(sampleRate, channels, bits);
        boolean bodyIsWav = isWav(format, request.getContentType());

        Thought thought;
        try (InputStream body = request.getInputStream()) {
            thought = store.store(
                    body,
                    pcmFormat,
                    bodyIsWav,
                    device,
                    parseCapturedAt(capturedAt),
                    request.getRemoteAddr());
        }

        log.info("captured {} from {} ({} ms, {} bytes)",
                thought.id(), thought.deviceId(), thought.durationMs(), thought.audioBytes());

        return ResponseEntity.created(URI.create("/api/thoughts/" + thought.id()))
                .body(new IngestResult(thought, transcribe(thought)));
    }

    /**
     * The capture is the record; the transcription is derived from it. A failing
     * speech-to-text engine must never cost the user a thought, so this returns
     * null instead of propagating.
     */
    private Transcription transcribe(Thought thought) {
        if (!sttProperties.isEnabled()) {
            return null;
        }
        try {
            Path audio = store.audioPath(thought);
            Transcription transcription = transcriber.transcribe(audio, thought.id());
            store.saveTranscription(transcription);
            return transcription;
        } catch (IOException | RuntimeException e) {
            log.warn("could not transcribe {}: {}", thought.id(), e.getMessage());
            return null;
        }
    }

    private static boolean isWav(String format, String contentType) {
        return "wav".equalsIgnoreCase(format)
                || (contentType != null && contentType.toLowerCase().contains("wav"));
    }

    /** Accepts ISO-8601 or epoch milliseconds; a device without a clock may omit it. */
    private static Instant parseCapturedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String trimmed = value.trim();
            return trimmed.chars().allMatch(Character::isDigit)
                    ? Instant.ofEpochMilli(Long.parseLong(trimmed))
                    : Instant.parse(trimmed);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid capturedAt: " + value);
        }
    }
}
