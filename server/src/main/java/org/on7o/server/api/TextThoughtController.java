package org.on7o.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;

/**
 * Text ingestion: a thought that arrives already written, with no device, no
 * audio and no speech-to-text step.
 *
 * <pre>
 * POST /api/thoughts/text
 * Content-Type: application/json
 *
 * {
 *   "text": "Almocei com o Bob hoje.",
 *   "capturedAt": "2026-08-24T12:30:00-03:00",
 *   "source": "synthetic"
 * }
 * </pre>
 *
 * <p>This exists so the HCIN work can run on synthetic fixtures with explicit
 * timestamps, without hardware and without waiting on Whisper. The thought it
 * creates is indistinguishable from a transcribed capture to every stage that
 * comes after it, and is served by the same {@code /api/thoughts} endpoints.
 */
@RestController
public class TextThoughtController {

    private final ThoughtService thoughts;

    public TextThoughtController(ThoughtService thoughts) {
        this.thoughts = thoughts;
    }

    /**
     * Stores a written thought and its transcription.
     *
     * @param request the text and the instant it was captured
     * @param http    the servlet request, for the caller's address
     * @return 201 with the created thought, or 400 when the body is invalid
     */
    @PostMapping(value = "/api/thoughts/text",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TextThoughtResponse> ingest(@Valid @RequestBody TextThoughtRequest request,
                                                      HttpServletRequest http) throws IOException {

        String text = request.trimmedText();
        String source = request.sourceOrDefault();

        Thought thought = thoughts.ingestText(
                text,
                request.capturedAt().toInstant(),
                source,
                http.getRemoteAddr());

        return ResponseEntity.created(URI.create("/api/thoughts/" + thought.id()))
                .body(new TextThoughtResponse(
                        thought.id(), text, request.capturedAt(), thought.receivedAt(), thought.source()));
    }
}
