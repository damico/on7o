package org.on7o.server.analysis;

import org.on7o.server.clarification.ClarificationQuestion;
import org.on7o.server.clarification.ClarificationService;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtNotFoundException;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.llm.QThoughtResult;
import org.on7o.server.llm.ThoughtInterpreter;
import org.on7o.server.stt.Transcription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Turns a transcription into semantic candidates and the questions needed to
 * pin them down.
 *
 * <p>This is the one implementation of stages 1 and 2. The synchronous REST
 * endpoint and the streaming endpoints the web UI uses both call in here, so
 * there is no second version of the pipeline to keep in step.
 *
 * <p>Analysis is idempotent: a thought whose artifacts already exist is read
 * back rather than sent to the model again, unless the caller forces it. That
 * is what makes the endpoint safe to retry and cheap to test.
 */
@Service
public class ThoughtAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ThoughtAnalysisService.class);

    private final ThoughtStore store;
    private final ThoughtInterpreter interpreter;
    private final ClarificationService clarification;

    public ThoughtAnalysisService(ThoughtStore store,
                                  ThoughtInterpreter interpreter,
                                  ClarificationService clarification) {
        this.store = store;
        this.interpreter = interpreter;
        this.clarification = clarification;
    }

    /**
     * Analyzes a thought end to end: semantic extraction, then clarification
     * questions.
     *
     * @param thoughtId the thought to analyze
     * @param force     re-run both stages even when their artifacts already exist
     * @return the questions the thought is now asking
     * @throws ThoughtNotFoundException when the thought does not exist
     * @throws IllegalArgumentException when the thought has nothing to analyze
     */
    public AnalysisResult analyze(String thoughtId, boolean force) throws IOException {
        requireAnalyzable(thoughtId);

        if (!force && isAnalyzed(thoughtId)) {
            log.debug("thought {} already analyzed, returning stored artifacts", thoughtId);
            return result(thoughtId, true, clarification.activeQuestions(thoughtId));
        }

        extractSemantics(thoughtId, force);
        return result(thoughtId, false, generateQuestions(thoughtId, force));
    }

    /**
     * Stage 1: extracts what the transcription states, as OWL Turtle, and saves
     * it as the thought's semantic artifact.
     *
     * @param force re-run even when the artifact already exists
     * @return the rThought Turtle
     */
    public String extractSemantics(String thoughtId, boolean force) throws IOException {
        Optional<String> existing = store.findRawThought(thoughtId);
        if (!force && existing.isPresent()) {
            return existing.get();
        }

        String text = requireAnalyzable(thoughtId);
        String turtle = interpreter.interpretRaw(text);
        store.saveRawThought(thoughtId, turtle);
        return turtle;
    }

    /**
     * Stage 2: asks what cannot be resolved from the text alone, saving both the
     * questions ontology and the questions themselves.
     *
     * <p>Regenerating retires the previous questions instead of deleting them,
     * so an answer the user already gave is never quietly lost.
     *
     * @param force regenerate even when questions already exist
     * @return the questions now being asked
     */
    public List<ClarificationQuestion> generateQuestions(String thoughtId, boolean force)
            throws IOException {

        if (!force && clarification.hasQuestions(thoughtId)) {
            return clarification.activeQuestions(thoughtId);
        }

        String rTurtle = store.findRawThought(thoughtId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no semantic artifact for thought " + thoughtId + "; analyze it first"));

        QThoughtResult generated = interpreter.questionRaw(rTurtle);
        store.saveQuestionsThought(thoughtId, generated.turtle());
        List<ClarificationQuestion> questions =
                clarification.replaceQuestions(thoughtId, generated.questions());

        log.info("thought {}: {} clarification question(s) generated", thoughtId, questions.size());
        return questions;
    }

    /** True when both stage artifacts are already on disk. */
    private boolean isAnalyzed(String thoughtId) {
        return store.findRawThought(thoughtId).isPresent() && clarification.hasQuestions(thoughtId);
    }

    /**
     * Checks that the thought exists and has something to analyze, and returns
     * its text.
     *
     * <p>A thought derived from an entity has no transcription and no rThought
     * stage: it is clarified through the entity pipeline instead, and analyzing
     * it here would silently produce nothing.
     */
    private String requireAnalyzable(String thoughtId) {
        Thought thought = store.find(thoughtId)
                .orElseThrow(() -> new ThoughtNotFoundException(thoughtId));

        if (thought.isDerived()) {
            throw new IllegalArgumentException(
                    "thought " + thoughtId + " was derived from an entity and has no transcription to analyze");
        }

        return store.findTranscription(thoughtId)
                .map(Transcription::text)
                .filter(text -> !text.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(
                        "no transcription for thought " + thoughtId));
    }

    private AnalysisResult result(String thoughtId, boolean reused, List<ClarificationQuestion> questions) {
        AnalysisStatus status = questions.stream().anyMatch(ClarificationQuestion::isOpen)
                ? AnalysisStatus.QUESTIONS_REQUIRED
                : AnalysisStatus.NO_QUESTIONS;

        return new AnalysisResult(thoughtId, status, reused,
                ThoughtStore.RTHOUGHT_FILE, ThoughtStore.QTHOUGHT_FILE, questions);
    }
}
