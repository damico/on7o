package org.on7o.server.api;

import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.ontology.OntologyDiagram;
import org.on7o.server.ontology.TurtleDiagramParser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Serves parsed ontology diagrams for the three interpretation stages, so the
 * viewer page can lay them out and draw them as SVG.
 */
@RestController
public class DiagramController {

    private static final Map<String, Function<ThoughtStore, Function<String, Optional<String>>>> STAGE_LOOKUPS = Map.of(
            "rthought", store -> store::findRawThought,
            "qthought", store -> store::findQuestionsThought,
            "cthought", store -> store::findConsolidatedThought
    );

    private final ThoughtStore store;
    private final TurtleDiagramParser parser;

    public DiagramController(ThoughtStore store, TurtleDiagramParser parser) {
        this.store = store;
        this.parser = parser;
    }

    /**
     * @param id    thought id
     * @param stage one of "rthought", "qthought", "cthought"
     * @return the diagram's nodes and edges
     */
    @GetMapping("/api/thoughts/{id}/diagram/{stage}")
    public OntologyDiagram diagram(@PathVariable String id, @PathVariable String stage) {
        Function<ThoughtStore, Function<String, Optional<String>>> lookup = STAGE_LOOKUPS.get(stage);
        if (lookup == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown stage: " + stage);
        }

        String turtle = lookup.apply(store).apply(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no " + stage + " found for thought: " + id));

        return parser.parse(turtle);
    }

    private static final Pattern SAFE_LOCAL_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * Manually asserts, from the diagram itself, that an existing individual is
     * also of an existing class - e.g. that Dr. Rubens is not only a
     * Psychiatrist but also a Person. Both names must already appear as nodes
     * in the current cThought; this only connects them, it never invents new
     * entities.
     *
     * @param id      thought id
     * @param request the individual and the additional type to assert
     * @return the updated cThought diagram
     */
    @PostMapping("/api/thoughts/{id}/diagram/cthought/relations")
    public OntologyDiagram addTypeRelation(@PathVariable String id, @RequestBody TypeAssertionRequest request) {
        String individual = request.individual();
        String type = request.type();
        if (individual == null || type == null
                || !SAFE_LOCAL_NAME.matcher(individual).matches()
                || !SAFE_LOCAL_NAME.matcher(type).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid individual or type name");
        }

        String turtle = store.findConsolidatedThought(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no cthought found for thought: " + id));

        OntologyDiagram diagram = parser.parse(turtle);
        boolean individualExists = diagram.nodes().stream().anyMatch(n -> n.label().equals(individual));
        boolean typeExists = diagram.nodes().stream()
                .anyMatch(n -> n.label().equals(type) && "class".equals(n.type()));
        if (!individualExists || !typeExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "individual or type not found in cThought");
        }

        try {
            store.appendConsolidatedType(id, individual, type);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to save relation");
        }

        String updated = store.findConsolidatedThought(id).orElse(turtle);
        return parser.parse(updated);
    }
}
