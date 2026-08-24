package org.on7o.server.ontology;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Counts what a Turtle artifact contains, so that a consolidation result can
 * report its size without the caller having to parse RDF.
 *
 * <p>The counts are deliberately simple and stated in the API rather than
 * inferred by the reader: an <em>entity</em> is a subject that was given a type
 * of its own, and a <em>relationship</em> is an assertion pointing at another
 * resource. Type declarations about the vocabulary itself, such as marking
 * something an {@code owl:Class}, describe the schema rather than the thought,
 * and are not counted as entities.
 */
@Service
public class TurtleMetrics {

    private static final Logger log = LoggerFactory.getLogger(TurtleMetrics.class);

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final Set<String> VOCABULARY_TYPES = Set.of(
            "http://www.w3.org/2002/07/owl#Class",
            "http://www.w3.org/2002/07/owl#ObjectProperty",
            "http://www.w3.org/2002/07/owl#DatatypeProperty",
            "http://www.w3.org/2002/07/owl#AnnotationProperty",
            "http://www.w3.org/2002/07/owl#Ontology",
            "http://www.w3.org/2000/01/rdf-schema#Class",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property");

    /**
     * Measures a Turtle artifact. Malformed input counts as nothing rather than
     * failing: a broken LLM response must not turn into a server error on a
     * request whose real work already succeeded.
     */
    public KnowledgeMetrics of(String turtle) {
        if (turtle == null || turtle.isBlank()) {
            return KnowledgeMetrics.empty();
        }

        Model model = ModelFactory.createDefaultModel();
        try {
            RDFParser.fromString(turtle).lang(Lang.TURTLE).parse(model);
        } catch (RuntimeException e) {
            log.warn("could not parse artifact for metrics: {}", e.getMessage());
            return KnowledgeMetrics.empty();
        }

        List<Triple> triples = model.getGraph().find(Node.ANY, Node.ANY, Node.ANY).toList();

        Set<String> entities = new HashSet<>();
        int relationships = 0;
        for (Triple triple : triples) {
            boolean isType = RDF_TYPE.equals(triple.getPredicate().getURI());
            if (isType) {
                if (isIndividualType(triple.getObject())) {
                    entities.add(label(triple.getSubject()));
                }
            } else if (triple.getObject().isURI() || triple.getObject().isBlank()) {
                relationships++;
            }
        }

        return new KnowledgeMetrics(triples.size(), entities.size(), relationships);
    }

    /** A type that says what something is in the world, rather than what it is in the vocabulary. */
    private static boolean isIndividualType(Node object) {
        return !object.isURI() || !VOCABULARY_TYPES.contains(object.getURI());
    }

    private static String label(Node node) {
        return node.isURI() ? node.getURI() : node.toString();
    }
}
