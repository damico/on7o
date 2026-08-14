package org.on7o.server.api;

import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.llm.InterpretationException;
import org.on7o.server.llm.QThoughtResult;
import org.on7o.server.llm.ThoughtInterpreter;
import org.on7o.server.stt.Transcription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * REST endpoints for the three-stage thought interpretation pipeline.
 *
 * <p>Stages 1 and 2 (rThought + qThought) are triggered together via a single
 * SSE stream opened by the "Understand" button. Stage 3 (cThought) is triggered
 * separately once the user has submitted answers to the clarification questions.
 *
 * <p>Both long-running operations run in virtual threads and push progress events
 * to the client via Server-Sent Events so no blocking occurs on Tomcat threads.
 */
@RestController
public class UnderstandController {

    private static final Logger log = LoggerFactory.getLogger(UnderstandController.class);
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ThoughtStore store;
    private final ThoughtInterpreter interpreter;

    public UnderstandController(ThoughtStore store, ThoughtInterpreter interpreter) {
        this.store = store;
        this.interpreter = interpreter;
    }

    /**
     * Opens an SSE stream that runs stages 1 (rThought) and 2 (qThought) in sequence.
     *
     * <p>Events emitted:
     * <ul>
     *   <li>{@code status} - progress label (analyzing, questioning)</li>
     *   <li>{@code rthought_done} - empty data, rThought saved to disk</li>
     *   <li>{@code questions_ready} - URL of the questions page</li>
     *   <li>{@code error} - message string on failure</li>
     * </ul>
     */
    @GetMapping(value = "/api/thoughts/{id}/understand/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter understandStream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Optional<Transcription> transcriptionOpt = store.findTranscription(id);
        if (transcriptionOpt.isEmpty()) {
            sendEvent(emitter, "error", "no transcription found for thought: " + id);
            emitter.complete();
            return emitter;
        }

        String transcriptionText = transcriptionOpt.get().text();

        Thread.ofVirtual().start(() -> {
            try {
                sendEvent(emitter, "status", "analyzing");
                String rTurtle = interpreter.interpretRaw(transcriptionText);
                store.saveRawThought(id, rTurtle);
                sendEvent(emitter, "rthought_done", "");

                sendEvent(emitter, "status", "questioning");
                QThoughtResult qt = interpreter.questionRaw(rTurtle);
                store.saveQuestionsThought(id, qt.turtle(), qt.questions());
                sendEvent(emitter, "questions_ready", "/thoughts/" + id + "/questions");

                emitter.complete();

            } catch (InterpretationException e) {
                log.warn("understand pipeline failed for {}: {}", id, e.getMessage());
                sendEvent(emitter, "error", e.getMessage());
                emitter.complete();
            } catch (IOException e) {
                log.error("could not persist interpretation for {}", id, e);
                sendEvent(emitter, "error", "failed to save interpretation");
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * Saves the user's answers to the clarification questions.
     * Must be called before opening the consolidate stream.
     *
     * @param id      thought id
     * @param answers list of plain-language answers, parallel to the stored question list
     */
    @PostMapping("/api/thoughts/{id}/answers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitAnswers(@PathVariable String id,
                              @RequestBody List<String> answers) throws IOException {
        if (store.find(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "thought not found: " + id);
        }
        store.saveAnswers(id, answers);
    }

    /**
     * Opens an SSE stream that runs stage 3 (cThought).
     * Requires that answers have already been submitted via {@link #submitAnswers}.
     *
     * <p>Events emitted:
     * <ul>
     *   <li>{@code status} - "consolidating"</li>
     *   <li>{@code result} - the final OWL 2 Turtle string (cThought)</li>
     *   <li>{@code error} - message string on failure</li>
     * </ul>
     */
    @GetMapping(value = "/api/thoughts/{id}/consolidate/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter consolidateStream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Optional<String> rThought = store.findRawThought(id);
        Optional<String> qThought = store.findQuestionsThought(id);
        Optional<List<String>> questions = store.findQuestions(id);
        Optional<List<String>> answers = store.findAnswers(id);

        if (rThought.isEmpty() || questions.isEmpty() || answers.isEmpty()) {
            sendEvent(emitter, "error",
                    "missing rThought, questions, or answers for thought: " + id);
            emitter.complete();
            return emitter;
        }

        String rTurtle = rThought.get();
        String qTurtle = qThought.orElse("");
        List<String> questionList = questions.get();
        List<String> answerList = answers.get();

        Thread.ofVirtual().start(() -> {
            try {
                sendEvent(emitter, "status", "consolidating");
                String cTurtle = interpreter.consolidate(rTurtle, qTurtle, questionList, answerList);
                store.saveConsolidatedThought(id, cTurtle);
                sendEvent(emitter, "result", cTurtle);
                emitter.complete();

            } catch (InterpretationException e) {
                log.warn("consolidation failed for {}: {}", id, e.getMessage());
                sendEvent(emitter, "error", e.getMessage());
                emitter.complete();
            } catch (IOException e) {
                log.error("could not persist cThought for {}", id, e);
                sendEvent(emitter, "error", "failed to save consolidated thought");
                emitter.complete();
            }
        });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.debug("SSE client disconnected during event '{}'", name);
        }
    }
}
