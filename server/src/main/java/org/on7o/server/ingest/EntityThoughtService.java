package org.on7o.server.ingest;

import org.on7o.server.llm.EntityCandidate;
import org.on7o.server.llm.InterpretationException;
import org.on7o.server.llm.QThoughtResult;
import org.on7o.server.llm.ThoughtInterpreter;
import org.on7o.server.ontology.DiagramNode;
import org.on7o.server.ontology.OntologyDiagram;
import org.on7o.server.ontology.TurtleDiagramParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Creates and grows the entity-derived thought branch: a qThought/cThought pair
 * seeded by a single entity found in another thought's stage, independent of
 * that thought's rThought.
 *
 * <p>Two entry points feed this branch:
 * <ul>
 *   <li>{@link #deriveEntity}: the user picks one node in a diagram and asks for it</li>
 *   <li>{@link #autoDeriveFromCThought}: run automatically once a cThought is consolidated</li>
 * </ul>
 */
@Service
public class EntityThoughtService {

    private static final Logger log = LoggerFactory.getLogger(EntityThoughtService.class);

    private final Map<String, Function<String, Optional<String>>> stageLookups;
    private final ThoughtStore store;
    private final ThoughtInterpreter interpreter;
    private final TurtleDiagramParser diagramParser;

    public EntityThoughtService(ThoughtStore store, ThoughtInterpreter interpreter,
                                TurtleDiagramParser diagramParser) {
        this.store = store;
        this.interpreter = interpreter;
        this.diagramParser = diagramParser;
        this.stageLookups = Map.of(
                "rthought", store::findRawThought,
                "qthought", store::findQuestionsThought,
                "cthought", store::findConsolidatedThought
        );
    }

    /**
     * Creates a new derived thought for one entity node picked by the user
     * from a diagram, and generates its clarification questions immediately.
     *
     * @param parentId    id of the thought whose diagram the entity came from
     * @param stage       the stage whose diagram the entity was picked from
     * @param entityLabel the entity's display label, exactly as shown in the diagram
     * @return the newly created, already-questioned thought
     */
    public Thought deriveEntity(String parentId, String stage, String entityLabel) throws IOException {
        Function<String, Optional<String>> lookup = stageLookups.get(stage);
        if (lookup == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown stage: " + stage);
        }

        String turtle = lookup.apply(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no " + stage + " found for thought: " + parentId));

        OntologyDiagram diagram = diagramParser.parse(turtle);
        DiagramNode node = diagram.nodes().stream()
                .filter(n -> n.label().equals(entityLabel))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "entity not found in " + stage + ": " + entityLabel));

        String context = node.tooltip() != null
                ? node.tooltip()
                : "Referenced as \"" + entityLabel + "\" with no further detail available.";

        return deriveAndQuestion(parentId, entityLabel, context);
    }

    /**
     * Scans a freshly consolidated cThought for entities that lack their own
     * definition and derives a thought for each, best-effort: a failure on one
     * candidate is logged and skipped rather than failing the whole scan.
     *
     * @param parentId       id of the thought whose cThought was just consolidated
     * @param cThoughtTurtle the consolidated ontology to scan
     */
    public void autoDeriveFromCThought(String parentId, String cThoughtTurtle) {
        List<EntityCandidate> candidates;
        try {
            candidates = interpreter.scanEntities(cThoughtTurtle);
        } catch (InterpretationException e) {
            log.warn("entity scan failed for {}: {}", parentId, e.getMessage());
            return;
        }

        for (EntityCandidate candidate : candidates) {
            try {
                Thought derived = deriveAndQuestion(parentId, candidate.label(), candidate.reason());
                log.info("auto-derived thought {} for entity '{}' from {}",
                        derived.id(), candidate.label(), parentId);
            } catch (InterpretationException | IOException e) {
                log.warn("could not auto-derive entity '{}' from {}: {}",
                        candidate.label(), parentId, e.getMessage());
            }
        }
    }

    /**
     * Creates and questions a new derived thought for the given entity, unless
     * one was already derived from this parent for the same entity - in which
     * case the existing thought is returned as-is, so repeated clicks or
     * repeated auto-scans across re-consolidations never produce duplicates.
     */
    private Thought deriveAndQuestion(String parentId, String entityLabel, String context) throws IOException {
        Optional<Thought> existing = store.findDerivedThoughts(parentId).stream()
                .filter(t -> entityLabel.equals(t.sourceEntity()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        Thought derived = store.createDerivedThought(parentId, entityLabel, context);
        QThoughtResult qt = interpreter.questionEntity(entityLabel, context);
        store.saveQuestionsThought(derived.id(), qt.turtle(), qt.questions());
        return derived;
    }
}
