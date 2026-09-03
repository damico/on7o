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
import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.hcin.HcinVocabulary;
import org.on7o.server.hcin.KnowledgeTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
            "http://www.w3.org/2002/07/owl#NamedIndividual",
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

    /**
     * Types that only say what something is in the vocabulary, not in the world.
     *
     * <p>{@code owl:NamedIndividual} belongs here for the same reason as the
     * rest: it says the term is an individual and not a class, which is a fact
     * about the ontology. Something typed that way and nothing else was never
     * said to be anything, and a thing the thought never described is not an
     * entity of the network.
     */
    private static boolean isVocabularyType(RDFNode type) {
        return type.isURIResource() && VOCABULARY_TYPES.contains(type.asResource().getURI());
    }

    private final HcinRepository repository;

    public CThoughtReader(HcinRepository repository) {
        this.repository = repository;
    }

    /**
     * Reads a consolidated thought.
     *
     * @param turtle the cThought as produced by consolidation
     * @return what can be merged, empty when the Turtle cannot be parsed at all
     */
    public CandidateKnowledge read(String turtle) {
        Model parsed = ModelFactory.createDefaultModel();
        try {
            RDFParser.fromString(turtle).lang(Lang.TURTLE).parse(parsed);
        } catch (RuntimeException e) {
            log.warn("could not parse the consolidated thought: {}", e.getMessage());
            return new CandidateKnowledge(List.of(), List.of(), ModelFactory.createDefaultModel());
        }

        Model model = inHcinTerms(parsed);
        Map<String, CandidateEntity> entities = readEntities(model);
        Map<Triple, Annotation> annotations = readAnnotations(model);
        Map<String, Annotation> bySubject = readSubjectAnnotations(model);

        return new CandidateKnowledge(
                List.copyOf(entities.values()),
                readStatements(model, entities, annotations, bySubject),
                readEvents(model));
    }

    /**
     * The same thought, said in the terms this model uses.
     *
     * <p>A consolidated ontology is written by a language model that knows the
     * standard vocabularies, so it may well say {@code foaf:Person} or
     * {@code org:Membership}. Those are not near-misses to be tolerated: the
     * ontology declares them equal to HCIN terms, so the honest reading is to
     * treat them as the same term rather than as something unrecognised.
     *
     * <p>Which terms are equal is the ontology's business, read from the schema
     * graph. Nothing here has to be updated when an alignment is added.
     */
    private Model inHcinTerms(Model parsed) {
        Map<String, String> aliases = repository.vocabularyAliases();
        if (aliases.isEmpty()) {
            return parsed;
        }

        Model canonical = ModelFactory.createDefaultModel();
        canonical.setNsPrefixes(parsed.getNsPrefixMap());
        int rewritten = 0;

        for (Statement statement : parsed.listStatements().toList()) {
            Resource subject = (Resource) translate(canonical, statement.getSubject(), aliases);
            Resource predicate = (Resource) translate(canonical, statement.getPredicate(), aliases);
            RDFNode object = translate(canonical, statement.getObject(), aliases);

            if (!subject.equals(statement.getSubject())
                    || !predicate.equals(statement.getPredicate())
                    || !object.equals(statement.getObject())) {
                rewritten++;
            }
            canonical.add(subject, canonical.createProperty(predicate.getURI()), object);
        }

        if (rewritten > 0) {
            log.info("read {} statement(s) written in another vocabulary as HCIN terms", rewritten);
        }
        return canonical;
    }

    /** One node in HCIN terms, or unchanged when it is nobody else's word for something. */
    private static RDFNode translate(Model canonical, RDFNode node, Map<String, String> aliases) {
        if (!node.isURIResource()) {
            return node;
        }
        String alias = aliases.get(node.asResource().getURI());
        return alias == null ? node : canonical.createResource(alias);
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
                                                    Map<Triple, Annotation> annotations,
                                                    Map<String, Annotation> bySubject) {
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

            Annotation annotation = annotations.containsKey(statement.asTriple())
                    ? annotations.get(statement.asTriple())
                    : bySubject.getOrDefault(source, Annotation.UNKNOWN);
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
                    || !EVENT_CLASSES.contains(typing.getObject().asResource().getURI())) {
                continue;
            }

            Resource node = typing.getSubject();
            Resource named = node.isURIResource() ? node : name(events, model, node);
            for (Statement statement : model.listStatements(node, null, (RDFNode) null).toList()) {
                events.add(named, statement.getPredicate(), statement.getObject());
            }
        }

        return events;
    }

    /**
     * A URI for an event the thought wrote as a blank node.
     *
     * <p>A membership is the case that keeps happening: the W3C organization
     * vocabulary recommends exactly this n-ary shape for a person holding a role
     * in an organization, and writing it anonymously is the idiomatic way. Blank
     * nodes cannot be reconciled, so such a node used to be dropped in silence,
     * taking with it the fact that someone runs a company.
     *
     * <p>The name comes from what the node says, never from the label the parser
     * gave it: that label is invented afresh on every read, and a URI built on it
     * would make a second copy of the same membership each time the thought was
     * reconciled again.
     */
    private static Resource name(Model events, Model model, Resource anonymous) {
        String content = model.listStatements(anonymous, null, (RDFNode) null).toList().stream()
                .map(statement -> statement.getPredicate().getURI() + "=" + valueOf(statement.getObject()))
                .sorted(Comparator.naturalOrder())
                .reduce("", (all, one) -> all + "|" + one);
        return events.createResource(HcinUris.anonymous(content));
    }

    /** What a node contributes to the identity of the anonymous node pointing at it. */
    private static String valueOf(RDFNode object) {
        if (object.isURIResource()) {
            return object.asResource().getURI();
        }
        return object.isLiteral() ? object.asLiteral().getLexicalForm() : "anonymous";
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

    /**
     * How a thought said it knows things, when it said so of an entity rather
     * than of a statement.
     *
     * <p>The consolidation prompt asks for RDF-star, because how something is
     * known is a claim about a claim and that is what RDF-star is for. Thoughts
     * consolidated before it asked clearly tagged the entity instead:
     * {@code :ninoska on7o:knowledgeStatus on7o:Asserted}. Strictly that says
     * nothing, since an entity is not a claim. Read as the writer plainly meant
     * it, it says how the statements starting at that entity are known, and
     * reading it that way is better than filing everything the user asserted as a
     * hypothesis, which is what happened until now.
     *
     * <p>It is a fallback and never wins: a statement carrying its own annotation
     * uses it.
     */
    private Map<String, Annotation> readSubjectAnnotations(Model model) {
        Map<String, Annotation> bySubject = new LinkedHashMap<>();

        for (Statement statement : model.listStatements().toList()) {
            if (!statement.getSubject().isURIResource()) {
                continue;
            }
            String subject = statement.getSubject().getURI();
            String predicate = statement.getPredicate().getURI();
            Annotation current = bySubject.getOrDefault(subject, Annotation.UNKNOWN);

            if (predicate.endsWith("knowledgeStatus")) {
                bySubject.put(subject, current.withTier(tierOf(statement.getObject())));
            } else if (predicate.endsWith("confidence")) {
                bySubject.put(subject, current.withConfidence(decimalOf(statement.getObject())));
            }
        }

        if (!bySubject.isEmpty()) {
            log.info("{} entity(ies) say how they are known instead of annotating their "
                    + "statements; read as the tier of what they state", bySubject.size());
        }
        return bySubject;
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
