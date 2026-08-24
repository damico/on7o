package org.on7o.server.api;

import org.on7o.server.analysis.ConsolidationResult;
import org.on7o.server.analysis.ConsolidationService;
import org.on7o.server.analysis.ConsolidationStatus;
import org.on7o.server.analysis.ThoughtAnalysisService;
import org.on7o.server.ingest.EntityThoughtService;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.llm.InterpretationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * The interpretation pipeline as the web UI drives it: one Server-Sent Events
 * stream per stage, because a model call takes long enough that a browser has to
 * be told what is happening.
 *
 * <p>These endpoints hold no logic of their own. Each stage is implemented once,
 * in {@link ThoughtAnalysisService} and {@link ConsolidationService}, and is
 * also reachable synchronously through {@link AnalysisController}. Pressing a
 * button in the UI means "do this now", so the streams always force a re-run
 * rather than returning a stored artifact.
 *
 * <p>Every stage runs in a virtual thread, so no Tomcat thread is held for the
 * length of a model call.
 */
@RestController
public class UnderstandController {

    private static final Logger log = LoggerFactory.getLogger(UnderstandController.class);
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ThoughtStore store;
    private final ThoughtAnalysisService analysis;
    private final ConsolidationService consolidation;
    private final EntityThoughtService entityThoughtService;

    public UnderstandController(ThoughtStore store,
                                ThoughtAnalysisService analysis,
                                ConsolidationService consolidation,
                                EntityThoughtService entityThoughtService) {
        this.store = store;
        this.analysis = analysis;
        this.consolidation = consolidation;
        this.entityThoughtService = entityThoughtService;
    }

    /**
     * Stage 1 (rThought): extracts what the transcription states, with no
     * inference and no claims.
     *
     * <p>Events emitted:
     * <ul>
     *   <li>{@code status} - "analyzing"</li>
     *   <li>{@code result} - the raw OWL 2 Turtle string</li>
     *   <li>{@code error} - message string on failure</li>
     * </ul>
     */
    @GetMapping(value = "/api/thoughts/{id}/rethought/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rethoughtStream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Thread.ofVirtual().start(() -> run(emitter, id, "analyzing", () -> {
            String turtle = analysis.extractSemantics(id, true);
            sendEvent(emitter, "result", turtle);
        }));

        return emitter;
    }

    /**
     * Stage 2 (qThought): asks what cannot be resolved from the text alone.
     *
     * <p>Events emitted:
     * <ul>
     *   <li>{@code status} - "questioning"</li>
     *   <li>{@code questions_ready} - URL of the questions page</li>
     *   <li>{@code error} - message string on failure</li>
     * </ul>
     */
    @GetMapping(value = "/api/thoughts/{id}/understand/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter understandStream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Thread.ofVirtual().start(() -> run(emitter, id, "questioning", () -> {
            analysis.generateQuestions(id, true);
            sendEvent(emitter, "questions_ready", "/thoughts/" + id + "/questions");
        }));

        return emitter;
    }

    /**
     * Stage 3 (cThought): consolidates the thought with the answers it received.
     *
     * <p>The user pressed Consolidate on a page that showed them every question,
     * so anything they left blank was left blank on purpose: this stream
     * proceeds without the missing answers rather than refusing. A captured
     * thought is then scanned in the background for entities worth a thought of
     * their own.
     *
     * <p>Events emitted:
     * <ul>
     *   <li>{@code status} - "consolidating"</li>
     *   <li>{@code result} - the final OWL 2 Turtle string</li>
     *   <li>{@code error} - message string on failure</li>
     * </ul>
     */
    @GetMapping(value = "/api/thoughts/{id}/consolidate/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter consolidateStream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Thread.ofVirtual().start(() -> run(emitter, id, "consolidating", () -> {
            ConsolidationResult result = consolidation.consolidate(id, true, true);
            if (result.status() != ConsolidationStatus.CONSOLIDATED) {
                sendEvent(emitter, "error", "could not consolidate thought: " + id);
                return;
            }

            String turtle = store.findConsolidatedThought(id).orElse("");
            sendEvent(emitter, "result", turtle);

            if (!store.find(id).map(Thought::isDerived).orElse(false)) {
                Thread.ofVirtual().start(() -> entityThoughtService.autoDeriveFromCThought(id, turtle));
            }
        }));

        return emitter;
    }

    /**
     * Runs one stage, reporting it over the stream and closing it either way.
     *
     * <p>A stage that fails is the user's problem to see, not just the log's:
     * every failure becomes an {@code error} event carrying a message they can
     * act on.
     */
    private void run(SseEmitter emitter, String id, String status, Stage stage) {
        try {
            sendEvent(emitter, "status", status);
            stage.execute();
        } catch (InterpretationException | IllegalArgumentException e) {
            log.warn("stage '{}' failed for {}: {}", status, id, e.getMessage());
            sendEvent(emitter, "error", e.getMessage());
        } catch (IOException e) {
            log.error("could not persist the '{}' stage for {}", status, id, e);
            sendEvent(emitter, "error", "failed to save the result");
        } finally {
            emitter.complete();
        }
    }

    /** One stage of the pipeline, as run inside a stream. */
    @FunctionalInterface
    private interface Stage {
        void execute() throws IOException;
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.debug("SSE client disconnected during event '{}'", name);
        }
    }
}
