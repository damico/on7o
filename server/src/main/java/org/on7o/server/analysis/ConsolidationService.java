package org.on7o.server.analysis;

import org.on7o.server.clarification.ClarificationService;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtNotFoundException;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.llm.ThoughtInterpreter;
import org.on7o.server.ontology.KnowledgeMetrics;
import org.on7o.server.ontology.TurtleMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Consolidates a thought: its semantics, the questions it raised and the answers
 * it received become one knowledge artifact.
 *
 * <p>What comes out is a <em>candidate</em>. It stays in the thought's own
 * directory and changes nothing global: merging it into the shared HCIN dataset
 * is reconciliation, a separate step with its own rules about what may be
 * promoted from hypothesis to fact.
 *
 * <p>Consolidation is repeatable. Asking twice returns the stored artifact
 * rather than paying for the model again, and forcing a re-run replaces the
 * artifact without touching the transcription, the questions or the answers
 * that produced it.
 */
@Service
public class ConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationService.class);

    private final ThoughtStore store;
    private final ThoughtInterpreter interpreter;
    private final ClarificationService clarification;
    private final TurtleMetrics metrics;

    public ConsolidationService(ThoughtStore store,
                                ThoughtInterpreter interpreter,
                                ClarificationService clarification,
                                TurtleMetrics metrics) {
        this.store = store;
        this.interpreter = interpreter;
        this.clarification = clarification;
        this.metrics = metrics;
    }

    /**
     * Consolidates a thought.
     *
     * @param thoughtId       the thought to consolidate
     * @param force           re-run the model even when a consolidated artifact exists
     * @param allowIncomplete consolidate even with required questions unanswered
     * @return the artifact and its size, or the ids of what is still missing
     * @throws ThoughtNotFoundException when the thought does not exist
     * @throws IllegalArgumentException when the thought has not been analyzed yet
     */
    public ConsolidationResult consolidate(String thoughtId, boolean force, boolean allowIncomplete)
            throws IOException {

        Thought thought = store.find(thoughtId)
                .orElseThrow(() -> new ThoughtNotFoundException(thoughtId));

        Optional<String> existing = store.findConsolidatedThought(thoughtId);
        if (!force && existing.isPresent()) {
            return consolidated(thoughtId, existing.get(), true);
        }

        List<String> open = clarification.unansweredRequiredIds(thoughtId);
        if (!open.isEmpty() && !allowIncomplete) {
            log.info("thought {} not consolidated: {} required question(s) unanswered",
                    thoughtId, open.size());
            return ConsolidationResult.blocked(thoughtId, open);
        }

        String turtle = interpret(thought);
        store.saveConsolidatedThought(thoughtId, turtle);
        log.info("thought {} consolidated ({} chars)", thoughtId, turtle.length());

        return consolidated(thoughtId, turtle, false);
    }

    /**
     * Runs the right consolidation prompt for the kind of thought.
     *
     * <p>A thought derived from an entity has no transcription behind it, so it
     * consolidates its own questions and answers alone; a captured or written
     * thought consolidates those on top of its extracted semantics.
     */
    private String interpret(Thought thought) {
        String id = thought.id();
        String qTurtle = store.findQuestionsThought(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no questions artifact for thought " + id + "; analyze it first"));

        ClarificationService.AlignedAnswers aligned = clarification.alignedForConsolidation(id);

        if (thought.isDerived()) {
            return interpreter.consolidateEntity(qTurtle, aligned.questions(), aligned.answers());
        }

        String rTurtle = store.findRawThought(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no semantic artifact for thought " + id + "; analyze it first"));

        return interpreter.consolidate(rTurtle, qTurtle, aligned.questions(), aligned.answers());
    }

    private ConsolidationResult consolidated(String thoughtId, String turtle, boolean reused) {
        KnowledgeMetrics measured = metrics.of(turtle);
        return new ConsolidationResult(thoughtId, ConsolidationStatus.CONSOLIDATED, reused,
                ThoughtStore.CTHOUGHT_FILE, measured, List.of());
    }
}
