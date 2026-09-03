package org.on7o.server.api;

import jakarta.servlet.http.HttpServletRequest;
import org.on7o.server.ingest.PcmFormat;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.ingest.TranscriptionWorker;
import org.on7o.server.stt.Transcription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;

/**
 * Audio ingestion. The device POSTs the captured bytes as the raw request
 * body, with no multipart and no envelope, so the firmware only has to write a
 * fixed header and then stream what the microphone produces.
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
 * <p><b>Transcription does not happen here.</b> The response says the thought is
 * stored, and speech-to-text runs afterwards on a background thread. Waiting for
 * Whisper would hold the request open for several times the length of the audio,
 * which is far longer than the eight seconds the device waits before it gives up
 * and reports a capture as lost.
 */
@RestController
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final ThoughtStore store;
    private final TranscriptionWorker transcriptionWorker;

    public IngestController(ThoughtStore store, TranscriptionWorker transcriptionWorker) {
        this.store = store;
        this.transcriptionWorker = transcriptionWorker;
    }

    /**
     * What the caller gets back: the capture, and what was understood from it.
     *
     * <p>The transcription is null on ingestion, because it has not been made
     * yet. It arrives under {@code GET /api/thoughts/{id}/transcription} once
     * the engine finishes.
     */
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

        transcriptionWorker.submit(thought);

        return ResponseEntity.created(URI.create("/api/thoughts/" + thought.id()))
                .body(new IngestResult(thought, null));
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
