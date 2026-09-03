package org.on7o.server.reconcile;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.on7o.server.hcin.HcinGraphs;
import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.hcin.HcinVocabulary;
import org.on7o.server.hcin.KnowledgeTier;
import org.on7o.server.hcin.RdfValues;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtNotFoundException;
import org.on7o.server.ingest.ThoughtStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Merges a consolidated thought into the HCIN.
 *
 * <p>This is where per-thought knowledge stops being a file and becomes part of
 * a network, and it is the step with the most opportunities to lie. Three rules
 * keep it honest.
 *
 * <p><b>Nothing is promoted by merging.</b> A statement arrives with the
 * epistemic status the thought gave it and lands in the matching graph. A guess
 * merged into the HCIN is still a guess.
 *
 * <p><b>Scope survives.</b> "Maria approved a payment related to the ABC
 * contract" must never become "Maria has permanent unrestricted financial
 * authority at ABC". Authority with no stated scope is therefore recorded as a
 * hypothesis no matter how confidently the thought stated it, and its missing
 * scope is left missing so the shapes can raise it as a question.
 *
 * <p><b>Nothing is overwritten.</b> Statements are only ever added, and the
 * graph a statement sits in says how far to trust it. A hypothesis that
 * contradicts a confirmed fact sits beside it rather than replacing it.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    /**
     * Properties reconciliation decides for itself.
     *
     * <p>A thought may say it is certain of something; where that certainty
     * lands is not the thought's call. Copying its own epistemic and provenance
     * statements across would leave a node claiming to be asserted while sitting
     * in the graph of things nobody has confirmed.
     */
    private static final Set<String> RECONCILER_OWNED = Set.of(
            HcinVocabulary.KNOWLEDGE_STATUS.getURI(),
            HcinVocabulary.CONFIDENCE.getURI(),
            HcinVocabulary.DERIVED_FROM.getURI(),
            HcinVocabulary.OBSERVED_AT.getURI(),
            HcinVocabulary.RECORDED_AT.getURI());

    private final ThoughtStore thoughts;
    private final CThoughtReader reader;
    private final EntityMatcher matcher;
    private final HcinRepository repository;

    public ReconciliationService(ThoughtStore thoughts,
                                 CThoughtReader reader,
                                 EntityMatcher matcher,
                                 HcinRepository repository) {
        this.thoughts = thoughts;
        this.reader = reader;
        this.matcher = matcher;
        this.repository = repository;
    }

    /**
     * Merges a thought's consolidated knowledge into the dataset.
     *
     * @param thoughtId the thought to merge
     * @return what was created, matched and asserted
     * @throws ThoughtNotFoundException when the thought does not exist
     * @throws IllegalArgumentException when it has not been consolidated yet
     */
    public ReconciliationResult reconcile(String thoughtId) {
        Thought thought = thoughts.find(thoughtId)
                .orElseThrow(() -> new ThoughtNotFoundException(thoughtId));

        String turtle = thoughts.findConsolidatedThought(thoughtId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "thought " + thoughtId + " has not been consolidated yet"));

        CandidateKnowledge candidates = reader.read(turtle);
        if (candidates.isEmpty()) {
            log.info("thought {} held nothing to reconcile", thoughtId);
            return ReconciliationResult.nothing(thoughtId);
        }

        Map<String, EntityMatch> matches = matcher.match(candidates.entities());
        Instant observedAt = thought.capturedAt();
        Instant recordedAt = Instant.now();

        Map<KnowledgeTier, Model> byTier = emptyModels();
        Model provenance = ModelFactory.createDefaultModel();
        Model evidence = evidenceOf(thought);

        writeEntities(matches, byTier, provenance, thoughtId, observedAt, recordedAt);
        int relationships = writeStatements(candidates, matches, byTier, provenance,
                thoughtId, observedAt, recordedAt);
        writeEvents(candidates, matches, byTier, provenance, thoughtId, observedAt, recordedAt);

        Map<KnowledgeTier, Long> added = commit(byTier, provenance, evidence);

        int created = (int) matches.values().stream().filter(EntityMatch::created).count();
        ReconciliationResult result = new ReconciliationResult(
                thoughtId,
                ReconciliationStatus.RECONCILED,
                created,
                matches.size() - created,
                relationships,
                added.get(KnowledgeTier.ASSERTED),
                added.get(KnowledgeTier.INFERRED),
                added.get(KnowledgeTier.HYPOTHESIZED));

        log.info("thought {} reconciled: {} entity(ies) created, {} matched, {} new statement(s)",
                thoughtId, created, matches.size() - created,
                added.values().stream().mapToLong(Long::longValue).sum());

        return result;
    }

    // -------------------------------------------------------------------------
    // Building what will be merged
    // -------------------------------------------------------------------------

    /**
     * Entities exist as facts. The user talked about Bob and clarified who he
     * was, so that Bob exists is not a guess, whatever the network goes on to
     * suspect about him.
     */
    private void writeEntities(Map<String, EntityMatch> matches,
                               Map<KnowledgeTier, Model> byTier,
                               Model provenance,
                               String thoughtId,
                               Instant observedAt,
                               Instant recordedAt) {

        Model asserted = byTier.get(KnowledgeTier.ASSERTED);

        for (EntityMatch match : matches.values()) {
            Resource entity = asserted.createResource(match.hcinUri());
            asserted.add(entity, RDF.type, match.candidate().kind().hcinClass());
            asserted.add(entity, HcinVocabulary.LABEL, match.candidate().label());

            observe(provenance, match.hcinUri(), thoughtId, observedAt, recordedAt);
        }
    }

    /**
     * Claims between entities become reified relationships, because an HCIN edge
     * is not a line: it has a direction, a provenance, a confidence and a status,
     * and it has to be able to disagree with another edge saying the opposite.
     */
    private int writeStatements(CandidateKnowledge candidates,
                                Map<String, EntityMatch> matches,
                                Map<KnowledgeTier, Model> byTier,
                                Model provenance,
                                String thoughtId,
                                Instant observedAt,
                                Instant recordedAt) {

        int written = 0;

        for (CandidateStatement statement : candidates.statements()) {
            EntityMatch source = matches.get(statement.sourceLocalUri());
            EntityMatch target = matches.get(statement.targetLocalUri());
            if (source == null || target == null) {
                continue;
            }

            Model model = byTier.get(statement.tier());
            String uri = HcinUris.relationship(source.hcinUri(), statement.predicateUri(), target.hcinUri());
            Resource relationship = model.createResource(uri);

            model.add(relationship, RDF.type, HcinVocabulary.RELATIONSHIP);
            if (isSocialTie(source, target)) {
                model.add(relationship, RDF.type, HcinVocabulary.SOCIAL_RELATIONSHIP);
            }
            model.add(relationship, HcinVocabulary.SOURCE, model.createResource(source.hcinUri()));
            model.add(relationship, HcinVocabulary.TARGET, model.createResource(target.hcinUri()));
            model.add(relationship, HcinVocabulary.RELATION_TYPE,
                    model.createResource(statement.predicateUri()));
            annotate(model, relationship, statement.tier(), statement.confidence(),
                    thoughtId, observedAt, recordedAt);

            observe(provenance, uri, thoughtId, observedAt, recordedAt);
            written++;
        }

        return written;
    }

    /**
     * Nodes already written in HCIN terms are carried over whole, with their own
     * URIs replaced by ones derived from the thought and their references to
     * entities rewritten to the matched HCIN entities.
     */
    private void writeEvents(CandidateKnowledge candidates,
                             Map<String, EntityMatch> matches,
                             Map<KnowledgeTier, Model> byTier,
                             Model provenance,
                             String thoughtId,
                             Instant observedAt,
                             Instant recordedAt) {

        Map<String, String> eventUris = new LinkedHashMap<>();
        Model events = candidates.events();

        for (Statement typing : events.listStatements(null, RDF.type, (RDFNode) null).toList()) {
            String local = typing.getSubject().getURI();
            String segment = segmentOf(typing.getObject());
            eventUris.put(local, HcinUris.event(segment, thoughtId, local));
        }

        for (Map.Entry<String, String> event : eventUris.entrySet()) {
            Resource local = events.getResource(event.getKey());
            KnowledgeTier tier = tierOfEvent(events, local);
            Model model = byTier.get(tier);
            Resource subject = model.createResource(event.getValue());

            for (Statement statement : events.listStatements(local, null, (RDFNode) null).toList()) {
                if (RECONCILER_OWNED.contains(statement.getPredicate().getURI())) {
                    continue;
                }
                model.add(subject, statement.getPredicate(),
                        remap(model, statement.getObject(), matches, eventUris));
            }

            layerOf(events, local).ifPresent(layer ->
                    model.add(subject, HcinVocabulary.LAYER, layer));

            annotate(model, subject, tier, null, thoughtId, observedAt, recordedAt);
            observe(provenance, event.getValue(), thoughtId, observedAt, recordedAt);
        }
    }

    /**
     * Where an event node belongs.
     *
     * <p>Authority with no stated scope is forced down to a hypothesis however
     * confidently it was stated. One approval is evidence of one approval, and
     * the difference between that and standing power is exactly what a scope
     * records.
     */
    private KnowledgeTier tierOfEvent(Model events, Resource node) {
        boolean isAuthority = events.contains(node, RDF.type, HcinVocabulary.FINANCIAL_AUTHORITY);
        boolean scoped = events.contains(node, HcinVocabulary.SCOPE);

        if (isAuthority && !scoped) {
            return KnowledgeTier.HYPOTHESIZED;
        }

        Statement status = events.getProperty(node, HcinVocabulary.KNOWLEDGE_STATUS);
        if (status == null || !status.getObject().isURIResource()) {
            return KnowledgeTier.HYPOTHESIZED;
        }
        return switch (status.getObject().asResource().getLocalName()) {
            case "Asserted" -> KnowledgeTier.ASSERTED;
            case "Inferred" -> KnowledgeTier.INFERRED;
            default -> KnowledgeTier.HYPOTHESIZED;
        };
    }

    /**
     * The relational layer a node belongs to, when its own class settles it.
     *
     * <p>Money moving and power over money are financial by construction, and
     * belonging to an organization is organizational. Anything else is left
     * without a layer: a plausible guess would be indistinguishable from
     * knowledge, and the shapes raise the absence as something to ask about.
     */
    private Optional<Resource> layerOf(Model events, Resource node) {
        if (events.contains(node, RDF.type, HcinVocabulary.FINANCIAL_FLOW)
                || events.contains(node, RDF.type, HcinVocabulary.FINANCIAL_AUTHORITY)) {
            return Optional.of(HcinVocabulary.FINANCIAL);
        }
        if (events.contains(node, RDF.type, HcinVocabulary.MEMBERSHIP)) {
            return Optional.of(HcinVocabulary.ORGANIZATIONAL);
        }
        return Optional.empty();
    }

    private RDFNode remap(Model model,
                          RDFNode object,
                          Map<String, EntityMatch> matches,
                          Map<String, String> eventUris) {

        if (!object.isURIResource()) {
            return object;
        }
        String uri = object.asResource().getURI();

        EntityMatch match = matches.get(uri);
        if (match != null) {
            return model.createResource(match.hcinUri());
        }
        String event = eventUris.get(uri);
        return event != null ? model.createResource(event) : object;
    }

    private static String segmentOf(RDFNode type) {
        return switch (type.asResource().getLocalName()) {
            case "Interaction" -> "interaction";
            case "Membership" -> "membership";
            case "FinancialFlow" -> "flow";
            case "FinancialAuthority" -> "authority";
            default -> "relationship";
        };
    }

    // -------------------------------------------------------------------------
    // Provenance
    // -------------------------------------------------------------------------

    /**
     * Whether an edge is a tie between two parties, rather than a statement of
     * some other kind that happens to have two ends.
     *
     * <p>Written here, where both ends and their kinds are known, rather than
     * asked of the graph later. What the shapes want to know about an edge, which
     * relational layer it lives in and which setting it belongs to, are questions
     * only a tie between people or organizations can answer, and the graph should
     * say which edges those are instead of every reader working it out again.
     *
     * <p>An edge from a node to itself is excluded even when that node is a
     * person. Reconciliation records identity as a relationship, and a person
     * being the same person as themselves has no layer and no setting: it is a
     * statement about naming, not about a tie.
     */
    private static boolean isSocialTie(EntityMatch source, EntityMatch target) {
        return !source.hcinUri().equals(target.hcinUri())
                && isSocial(source) && isSocial(target);
    }

    /** Whether one end of an edge is a person or an organization. */
    private static boolean isSocial(EntityMatch end) {
        EntityKind kind = end.candidate().kind();
        return kind == EntityKind.PERSON || kind == EntityKind.ORGANIZATION;
    }

    /** Says how a statement is known, where it came from, and when it was learned. */
    private void annotate(Model model,
                          Resource subject,
                          KnowledgeTier tier,
                          BigDecimal confidence,
                          String thoughtId,
                          Instant observedAt,
                          Instant recordedAt) {

        model.add(subject, HcinVocabulary.KNOWLEDGE_STATUS, statusOf(tier));
        model.add(subject, HcinVocabulary.DERIVED_FROM, model.createResource(HcinUris.thought(thoughtId)));
        model.add(subject, HcinVocabulary.OBSERVED_AT, RdfValues.dateTime(observedAt));
        model.add(subject, HcinVocabulary.RECORDED_AT, RdfValues.dateTime(recordedAt));

        if (confidence != null) {
            model.add(subject, HcinVocabulary.CONFIDENCE, RdfValues.decimal(confidence));
        }
    }

    /** Records the act of learning one thing from one thought. */
    private void observe(Model provenance,
                         String aboutUri,
                         String thoughtId,
                         Instant observedAt,
                         Instant recordedAt) {

        Resource observation = provenance.createResource(HcinUris.observation(thoughtId, aboutUri));
        provenance.add(observation, RDF.type, HcinVocabulary.OBSERVATION);
        provenance.add(observation, HcinVocabulary.OBSERVED_STATEMENT, provenance.createResource(aboutUri));
        provenance.add(observation, HcinVocabulary.THOUGHT_ID, thoughtId);
        provenance.add(observation, HcinVocabulary.DERIVED_FROM,
                provenance.createResource(HcinUris.thought(thoughtId)));
        provenance.add(observation, HcinVocabulary.OBSERVED_AT, RdfValues.dateTime(observedAt));
        provenance.add(observation, HcinVocabulary.RECORDED_AT, RdfValues.dateTime(recordedAt));
    }

    /** The thought itself, as the evidence everything merged from it points back to. */
    private Model evidenceOf(Thought thought) {
        Model model = ModelFactory.createDefaultModel();
        Resource evidence = model.createResource(HcinUris.thought(thought.id()));

        model.add(evidence, RDF.type, HcinVocabulary.EVIDENCE);
        model.add(evidence, HcinVocabulary.THOUGHT_ID, thought.id());
        model.add(evidence, HcinVocabulary.OCCURRED_AT, RdfValues.dateTime(thought.capturedAt()));

        thoughts.findTranscription(thought.id())
                .ifPresent(transcription -> model.add(evidence, HcinVocabulary.LABEL, transcription.text()));

        return model;
    }

    // -------------------------------------------------------------------------
    // Committing
    // -------------------------------------------------------------------------

    /**
     * Writes every tier and counts what was genuinely new.
     *
     * <p>Counting by the change in graph size rather than by what was offered is
     * what makes a repeated reconciliation report zero instead of pretending to
     * have added the same statements again.
     */
    private Map<KnowledgeTier, Long> commit(Map<KnowledgeTier, Model> byTier,
                                            Model provenance,
                                            Model evidence) {

        Map<KnowledgeTier, Long> added = new EnumMap<>(KnowledgeTier.class);

        for (Map.Entry<KnowledgeTier, Model> tier : byTier.entrySet()) {
            String graph = tier.getKey().graph();
            long before = repository.size(graph);
            repository.add(graph, tier.getValue());
            added.put(tier.getKey(), repository.size(graph) - before);
        }

        repository.add(HcinGraphs.PROVENANCE, provenance);
        repository.add(HcinGraphs.THOUGHTS, evidence);
        return added;
    }

    private static Map<KnowledgeTier, Model> emptyModels() {
        Map<KnowledgeTier, Model> models = new EnumMap<>(KnowledgeTier.class);
        for (KnowledgeTier tier : KnowledgeTier.values()) {
            models.put(tier, ModelFactory.createDefaultModel());
        }
        return models;
    }

    private static Resource statusOf(KnowledgeTier tier) {
        return switch (tier) {
            case ASSERTED -> HcinVocabulary.ASSERTED;
            case INFERRED -> HcinVocabulary.INFERRED;
            case HYPOTHESIZED -> HcinVocabulary.HYPOTHESIZED;
        };
    }
}
