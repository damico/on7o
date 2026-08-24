package org.on7o.server.hcin;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Validates HCIN data against the packaged shapes.
 *
 * <p>The point is not to police the data. It is to find out what the data does
 * not say: a financial flow with no amount, an authority with no scope. Those
 * come back as clarification candidates, which is exactly what the question
 * pipeline is for.
 */
@Service
public class ShaclValidationService {

    private static final Logger log = LoggerFactory.getLogger(ShaclValidationService.class);
    private static final String SHAPES_ARTIFACT = "hcin-shapes.ttl";

    private final HcinRepository repository;
    private final Shapes shapes;

    public ShaclValidationService(HcinRepository repository) throws IOException {
        this.repository = repository;

        Model shapesModel = ModelFactory.createDefaultModel();
        RDFParser.fromString(HcinDataset.readArtifact(SHAPES_ARTIFACT)).lang(Lang.TURTLE).parse(shapesModel);
        this.shapes = Shapes.parse(shapesModel.getGraph());

        log.info("HCIN shapes loaded: {} shape(s)", shapes.numRootShapes());
    }

    /** Validates a model that is not in the dataset yet, such as a candidate about to be merged. */
    public ShaclReport validate(Model data) {
        return report(ShaclValidator.get().validate(shapes, data.getGraph()));
    }

    /** Validates one named graph of the dataset. */
    public ShaclReport validateGraph(String graphUri) {
        Graph graph = repository.copyOf(graphUri).getGraph();
        return report(ShaclValidator.get().validate(shapes, graph));
    }

    /** Findings, most severe first, so that whatever matters most is read first. */
    private ShaclReport report(ValidationReport report) {
        List<ShaclFinding> findings = report.getEntries().stream()
                .map(ShaclValidationService::finding)
                .sorted(Comparator.comparing(ShaclFinding::severity))
                .toList();

        return new ShaclReport(report.conforms(), findings);
    }

    private static ShaclFinding finding(ReportEntry entry) {
        return new ShaclFinding(
                ShaclSeverity.of(uri(entry.severity() == null ? null : entry.severity().level())),
                text(entry.focusNode()),
                text(entry.resultPath() == null ? null : entry.resultPath().toString()),
                entry.message(),
                text(entry.source()));
    }

    private static String uri(org.apache.jena.graph.Node node) {
        return node != null && node.isURI() ? node.getURI() : null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof org.apache.jena.graph.Node node) {
            return node.isURI() ? node.getURI() : node.toString();
        }
        return value.toString();
    }
}
