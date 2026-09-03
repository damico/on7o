package org.on7o.server.api;

import org.on7o.server.clarification.NetworkClarificationService;
import org.on7o.server.hcin.HcinDataset;
import org.on7o.server.hcin.HcinGraphs;
import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.hcin.ShaclReport;
import org.on7o.server.hcin.ShaclValidationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The HCIN, readable from outside.
 *
 * <pre>
 * GET /api/hcin/artifacts
 * GET /api/hcin/artifacts/{name}
 * GET /api/hcin/data
 * GET /api/hcin/data?graph=asserted
 * GET /api/hcin/validate?graph=asserted
 * POST /api/hcin/clarifications
 * </pre>
 *
 * <p>Exists so that the semantic layer can be inspected without reading the
 * filesystem or opening a TDB2 store by hand. The data endpoints serialize out
 * of the dataset every time rather than serving a stored copy: a second file
 * claiming to be the truth would eventually disagree with the first.
 *
 * <p>Only the packaged artifacts and the known graphs can be named. Nothing here
 * turns a caller's string into a path.
 */
@RestController
public class HcinController {

    private static final String TURTLE = "text/turtle";

    private final HcinRepository repository;
    private final ShaclValidationService validation;
    private final NetworkClarificationService networkClarification;

    public HcinController(HcinRepository repository,
                          ShaclValidationService validation,
                          NetworkClarificationService networkClarification) {
        this.repository = repository;
        this.validation = validation;
        this.networkClarification = networkClarification;
    }

    /** The schema files this build ships, and the exports that can be generated. */
    @GetMapping(value = "/api/hcin/artifacts", produces = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeArtifactsResponse artifacts() {
        List<KnowledgeArtifactDto> artifacts = new ArrayList<>();

        for (String name : HcinDataset.SCHEMA_ARTIFACTS) {
            artifacts.add(new KnowledgeArtifactDto(name, TURTLE, kindOf(name)));
        }
        artifacts.add(new KnowledgeArtifactDto("hcin-data.ttl", TURTLE,
                KnowledgeArtifactDto.ArtifactKind.DATASET_EXPORT));

        return new KnowledgeArtifactsResponse(artifacts);
    }

    /**
     * One artifact.
     *
     * @param name a packaged schema file, or {@code hcin-data.ttl} for the whole dataset
     */
    @GetMapping(value = "/api/hcin/artifacts/{name}", produces = TURTLE)
    public ResponseEntity<String> artifact(@PathVariable String name) throws IOException {
        if ("hcin-data.ttl".equals(name)) {
            return turtle(repository.exportAll());
        }
        return turtle(HcinDataset.readArtifact(name));
    }

    /**
     * The dataset as Turtle.
     *
     * @param graph a graph short name such as {@code asserted}, or absent for everything
     */
    @GetMapping(value = "/api/hcin/data", produces = TURTLE)
    public ResponseEntity<String> data(@RequestParam(required = false) String graph) {
        if (graph == null || graph.isBlank()) {
            return turtle(repository.exportAll());
        }
        return turtle(repository.export(HcinGraphs.resolve(graph)));
    }

    /**
     * What the shapes have to say about a graph.
     *
     * <p>Findings marked as clarification candidates are not defects: they are
     * the gaps worth asking a person about.
     *
     * @param graph a graph short name, defaulting to the asserted facts
     */
    @GetMapping(value = "/api/hcin/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ShaclReport validate(@RequestParam(defaultValue = "asserted") String graph) {
        return validation.validateGraph(HcinGraphs.resolve(graph));
    }

    /**
     * Asks the network about its own gaps.
     *
     * <p>Every other question in on7o starts from something the user said. This
     * one starts from what the shapes know an HCIN statement is meant to say, so
     * the network asks about what it is missing rather than waiting for a thought
     * to raise the subject.
     *
     * <p>Safe to call repeatedly: a gap already asked about keeps the question it
     * produced, in whatever state the user left it.
     */
    @PostMapping(value = "/api/hcin/clarifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public NetworkClarificationResponse askAboutGaps() throws IOException {
        NetworkClarificationService.Result result = networkClarification.askAboutGaps();
        return NetworkClarificationResponse.of(
                result.gapsFound(), result.asked(), result.unattributed());
    }

    private static KnowledgeArtifactDto.ArtifactKind kindOf(String name) {
        if (name.contains("shapes")) {
            return KnowledgeArtifactDto.ArtifactKind.SHAPES;
        }
        return name.contains("core")
                ? KnowledgeArtifactDto.ArtifactKind.SCHEMA
                : KnowledgeArtifactDto.ArtifactKind.VOCABULARY;
    }

    private static ResponseEntity<String> turtle(String body) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(TURTLE)).body(body);
    }
}
