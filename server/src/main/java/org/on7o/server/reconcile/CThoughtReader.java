package org.on7o.server.reconcile;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.on7o.server.hcin.HcinVocabulary;
import org.on7o.server.hcin.KnowledgeTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a consolidated thought and works out what of it can be merged.
 *
 * <p>The consolidated ontology is written by a language model against a loose
 * vocabulary, so this reader takes it as suggestive rather than authoritative:
 * it picks out what it recognises and quietly leaves the rest, instead of
 * failing on a shape it did not expect.
 *
 * <p>Three things are recognised. Individuals, meaning subjects given a type
 * that is not itself vocabulary. Claims between two of those individuals. And
 * nodes already typed with an HCIN class, which are carried over whole because
 * an interaction or a financial flow says more than a triple can.
 */
@Service
public class CThoughtReader {

    private static final Logger log = LoggerFactory.getLogger(CThoughtReader.class);

    private static final String ON7O_NS = "http://on7o.io/ontology#";
    private static final Set<String> VOCABULARY_TYPES = Set.of(
            "http://www.w3.org/2002/07/owl#Class",
            "http://www.w3.org/2002/07/owl#ObjectProperty",
            "http://www.w3.org/2002/07/owl#DatatypeProperty",
            "http://www.w3.org/2002/07/owl#AnnotationProperty",
            "http://www.w3.org/2002/07/owl#Ontology",
            "http://www.w3.org/2000/01/rdf-schema#Class",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property");

    /** Classes whose instances are carried over as whole nodes. */
    private static final Set<String> EVENT_CLASSES = Set.of(
            HcinVocabulary.INTERACTION.getURI(),
            HcinVocabulary.MEMBERSHIP.getURI(),
            HcinVocabulary.RELATIONSHIP.getURI(),
            HcinVocabulary.FINANCIAL_FLOW.getURI(),
            HcinVocabulary.FINANCIAL_AUTHORITY.getURI());

    /** Types that only say what something is in the vocabulary, not in the world. */
    private static boolean isVocabularyType(RDFNode type) {
        return type.isURIResource() && VOCABULARY_TYPES.contains(type.asResource().getURI());
    }

    /**
     * Reads a consolidated thought.
     *
     * @param turtle the cThought as produced by consolidation
     * @return what can be merged, empty when the Turtle cannot be parsed at all
     */
    public CandidateKnowledge read(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        try {
            RDFParser.fromString(turtle).lang(Lang.TURTLE).parse(model);
        } catch (RuntimeException e) {
            log.warn("could not parse the consolidated thought: {}", e.getMessage());
            return new CandidateKnowledge(List.of(), List.of(), ModelFactory.createDefaultModel());
        }

        Map<String, CandidateEntity> entities = readEntities(model);
        Map<Triple, Annotation> annotations = readAnnotations(model);

        return new CandidateKnowledge(
                List.copyOf(entities.values()),
                readStatements(model, entities, annotations),
                readEvents(model));
    }

    /**
     * Individuals: anything given a type of its own that is not a vocabulary
     * type, and that is not one of the event nodes carried over separately.
     */
    private Map<String, CandidateEntity> readEntities(Model model) {
        Map<String, CandidateEntity> entities = new LinkedHashMap<>();

        for (Statement statement : model.listStatements(null, RDF.type, (RDFNode) null).toList()) {
            if (!statement.getSubject().isURIResource() || isVocabularyType(statement.getObject())) {
                continue;
            }
            String typeUri = statement.getObject().isURIResource()
                    ? statement.getObject().asResource().getURI() : null;
            if (typeUri != null && EVENT_CLASSES.contains(typeUri)) {
                continue;
            }

            Resource subject = statement.getSubject();
            EntityKind kind = EntityKind.fromTypeName(localName(typeUri));
            String label = labelOf(model, subject);

            entities.merge(subject.getURI(), new CandidateEntity(subject.getURI(), label, kind),
                    CThoughtReader::moreSpecific);
        }

        return entities;
    }

    /** A typed guess beats an untyped one, so two types on one subject keep the useful one. */
    private static CandidateEntity moreSpecific(CandidateEntity existing, CandidateEntity candidate) {
        return existing.kind() == EntityKind.OTHER ? candidate : existing;
    }

    /** Claims linking two individuals, carrying whatever the thought said about believing them. */
    private List<CandidateStatement> readStatements(Model model,
                                                    Map<String, CandidateEntity> entities,
                                                    Map<Triple, Annotation> annotations) {
        List<CandidateStatement> statements = new ArrayList<>();

        for (Statement statement : model.listStatements().toList()) {
            if (statement.getPredicate().equals(RDF.type)
                    || !statement.getSubject().isURIResource()
                    || !statement.getObject().isURIResource()) {
                continue;
            }

            String source = statement.getSubject().getURI();
            String target = statement.getObject().asResource().getURI();
            if (!entities.containsKey(source) || !entities.containsKey(target)) {
                continue;
            }

            Annotation annotation = annotations.getOrDefault(statement.asTriple(), Annotation.UNKNOWN);
            statements.add(new CandidateStatement(source, statement.getPredicate().getURI(), target,
                    annotation.tier(), annotation.confidence()));
        }

        return statements;
    }

    /**
     * The nodes already written in HCIN terms, with everything said about them.
     *
     * <p>Copied rather than interpreted: an interaction carries participants, a
     * time, a type and a context, and flattening that into triples between two
     * entities would throw away the parts the projection actually needs.
     */
    private Model readEvents(Model model) {
        Model events = ModelFactory.createDefaultModel();

        for (Statement typing : model.listStatements(null, RDF.type, (RDFNode) null).toList()) {
            if (!typing.getObject().isURIResource()
                    || !EVENT_CLASSES.contains(typing.getObject().asResource().getURI())
                    || !typing.getSubject().isURIResource()) {
                continue;
            }
            events.add(model.listStatements(typing.getSubject(), null, (RDFNode) null));
        }

        return events;
    }

    /**
     * The RDF-star annotations the consolidation prompt asks for: how a
     * statement is known and how strongly.
     */
    private Map<Triple, Annotation> readAnnotations(Model model) {
        Map<Triple, Annotation> annotations = new LinkedHashMap<>();

        for (Statement statement : model.listStatements().toList()) {
            Node subject = statement.getSubject().asNode();
            if (!subject.isNodeTriple()) {
                continue;
            }

            Triple annotated = subject.getTriple();
            Annotation current = annotations.getOrDefault(annotated, Annotation.UNKNOWN);
            String predicate = statement.getPredicate().getURI();

            if (predicate.endsWith("knowledgeStatus")) {
                annotations.put(annotated, current.withTier(tierOf(statement.getObject())));
            } else if (predicate.endsWith("confidence")) {
                annotations.put(annotated, current.withConfidence(decimalOf(statement.getObject())));
            }
        }

        return annotations;
    }

    private static KnowledgeTier tierOf(RDFNode node) {
        String name = node.isURIResource() ? localName(node.asResource().getURI()) : String.valueOf(node);
        return switch (name) {
            case "Asserted" -> KnowledgeTier.ASSERTED;
            case "Inferred" -> KnowledgeTier.INFERRED;
            default -> KnowledgeTier.HYPOTHESIZED;
        };
    }

    private static BigDecimal decimalOf(RDFNode node) {
        try {
            return node.isLiteral() ? new BigDecimal(node.asLiteral().getLexicalForm()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String labelOf(Model model, Resource subject) {
        Statement label = model.getProperty(subject, RDFS.label);
        if (label != null && label.getObject().isLiteral()) {
            return label.getString();
        }
        Statement hcinLabel = model.getProperty(subject, HcinVocabulary.LABEL);
        if (hcinLabel != null && hcinLabel.getObject().isLiteral()) {
            return hcinLabel.getString();
        }
        return humanize(localName(subject.getURI()));
    }

    private static String localName(String uri) {
        if (uri == null) {
            return null;
        }
        int cut = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
        return cut >= 0 ? uri.substring(cut + 1) : uri;
    }

    /** Turns an ontology local name such as {@code AcmeCompany} back into words. */
    private static String humanize(String localName) {
        if (localName == null || localName.isBlank()) {
            return "unnamed";
        }
        return localName.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
    }

    /** What a thought said about believing one of its own statements. */
    private record Annotation(KnowledgeTier tier, BigDecimal confidence) {

        /** A statement the thought made no epistemic claim about. */
        static final Annotation UNKNOWN = new Annotation(KnowledgeTier.HYPOTHESIZED, null);

        Annotation withTier(KnowledgeTier newTier) {
            return new Annotation(newTier, confidence);
        }

        Annotation withConfidence(BigDecimal newConfidence) {
            return new Annotation(tier, newConfidence);
        }
    }

    /** The namespace the interpretation prompts write their ontologies in. */
    public static String on7oNamespace() {
        return ON7O_NS;
    }
}
