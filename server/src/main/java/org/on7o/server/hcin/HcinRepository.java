package org.on7o.server.hcin;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reading the HCIN.
 *
 * <p>Every query is answered as of a given instant, and nothing that happened
 * after that instant is allowed to influence the answer. That is what makes it
 * possible to ask what the network looked like last quarter without unwinding
 * anything.
 *
 * <p>Queries span the three knowledge graphs at once, and every record says
 * which one it came from. A caller that must not treat a hypothesis as a fact
 * can therefore tell them apart, instead of being handed a flat truth.
 */
@Service
public class HcinRepository {

    private static final String KNOWLEDGE_GRAPHS = """
            VALUES ?g { <%s> <%s> <%s> }
            """.formatted(HcinGraphs.ASSERTED, HcinGraphs.INFERRED, HcinGraphs.HYPOTHESES);

    private final HcinTransactions transactions;
    private final HcinDataset hcin;

    public HcinRepository(HcinTransactions transactions, HcinDataset hcin) {
        this.transactions = transactions;
        this.hcin = hcin;
    }

    /** The ego every projection is centred on. */
    public String ego() {
        return hcin.ego();
    }

    // -------------------------------------------------------------------------
    // Entities
    // -------------------------------------------------------------------------

    /** Everyone the network knows about, the ego included. */
    public List<HcinEntity> people() {
        return entitiesOfClass("hcin:Person");
    }

    /** Every organization the network knows about. */
    public List<HcinEntity> organizations() {
        return entitiesOfClass("hcin:Organization");
    }

    private List<HcinEntity> entitiesOfClass(String type) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?uri ?label ?g WHERE {
                  %s
                  GRAPH ?g {
                    ?uri a %s .
                    OPTIONAL { ?uri hcin:label ?label }
                  }
                }
                """.formatted(KNOWLEDGE_GRAPHS, type);

        return select(query, null, solution -> new HcinEntity(
                RdfValues.toUri(solution.get("uri")),
                RdfValues.toString(solution.get("label")),
                KnowledgeTier.of(RdfValues.toUri(solution.get("g")))));
    }

    /**
     * Who belonged to what, as of the given instant.
     *
     * <p>A membership with no start date falls back to when it was observed. The
     * system cannot claim someone belonged somewhere before it had any reason to
     * think so, and letting an unbounded fact stretch backwards would let what
     * was learned in August change what the network looked like in January.
     */
    public List<HcinMembership> memberships(Instant asOf) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?uri ?person ?org ?role ?from ?to ?g WHERE {
                  %s
                  GRAPH ?g {
                    ?uri a hcin:Membership ;
                         hcin:member   ?person ;
                         hcin:memberOf ?org .
                    OPTIONAL { ?uri hcin:role ?role }
                    OPTIONAL { ?uri hcin:validFrom ?from }
                    OPTIONAL { ?uri hcin:validTo ?to }
                    OPTIONAL { ?uri hcin:observedAt ?obs }
                  }
                  FILTER (COALESCE(?from, ?obs, ?asOf) <= ?asOf)
                  FILTER (!BOUND(?to) || ?to >= ?asOf)
                }
                """.formatted(KNOWLEDGE_GRAPHS);

        return select(query, asOf, solution -> new HcinMembership(
                RdfValues.toUri(solution.get("uri")),
                RdfValues.toUri(solution.get("person")),
                RdfValues.toUri(solution.get("org")),
                RdfValues.toString(solution.get("role")),
                RdfValues.toInstant(solution.get("from")),
                RdfValues.toInstant(solution.get("to")),
                KnowledgeTier.of(RdfValues.toUri(solution.get("g")))));
    }

    // -------------------------------------------------------------------------
    // Interactions
    // -------------------------------------------------------------------------

    /**
     * Every interaction between the ego and someone else, up to the given
     * instant. Anything later simply does not exist yet from that vantage point.
     */
    public List<HcinInteraction> interactionsWithEgo(Instant asOf) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?uri ?other ?at ?type ?ctx ?g WHERE {
                  %s
                  GRAPH ?g {
                    ?uri a hcin:Interaction ;
                         hcin:participant ?ego ;
                         hcin:participant ?other ;
                         hcin:occurredAt  ?at .
                    OPTIONAL { ?uri hcin:interactionType ?type }
                    OPTIONAL { ?uri hcin:context ?ctx }
                  }
                  FILTER (?other != ?ego)
                  FILTER (?at <= ?asOf)
                }
                """.formatted(KNOWLEDGE_GRAPHS);

        return select(query, asOf, solution -> new HcinInteraction(
                RdfValues.toUri(solution.get("uri")),
                RdfValues.toUri(solution.get("other")),
                RdfValues.toInstant(solution.get("at")),
                RdfValues.toString(solution.get("type")),
                RdfValues.toUri(solution.get("ctx")),
                KnowledgeTier.of(RdfValues.toUri(solution.get("g")))));
    }

    // -------------------------------------------------------------------------
    // Money
    // -------------------------------------------------------------------------

    /** Every financial flow the ego is one end of, up to the given instant. */
    public List<HcinFinancialFlow> flowsInvolvingEgo(Instant asOf) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?uri ?src ?tgt ?dir ?amount ?currency ?at ?g WHERE {
                  %s
                  GRAPH ?g {
                    ?uri a hcinf:FinancialFlow ;
                         hcinf:flowSource ?src ;
                         hcinf:flowTarget ?tgt ;
                         hcin:occurredAt  ?at .
                    OPTIONAL { ?uri hcinf:direction ?dir }
                    OPTIONAL { ?uri hcinf:amount    ?amount }
                    OPTIONAL { ?uri hcinf:currency  ?currency }
                  }
                  FILTER (?src = ?ego || ?tgt = ?ego)
                  FILTER (?at <= ?asOf)
                }
                """.formatted(KNOWLEDGE_GRAPHS);

        return select(query, asOf, solution -> new HcinFinancialFlow(
                RdfValues.toUri(solution.get("uri")),
                RdfValues.toUri(solution.get("src")),
                RdfValues.toUri(solution.get("tgt")),
                FlowDirection.of(RdfValues.toUri(solution.get("dir"))),
                RdfValues.toDecimal(solution.get("amount")),
                RdfValues.toString(solution.get("currency")),
                RdfValues.toInstant(solution.get("at")),
                KnowledgeTier.of(RdfValues.toUri(solution.get("g")))));
    }

    /**
     * Who held financial authority as of the given instant.
     *
     * <p>Authority that ended before that instant is not returned: someone who
     * could approve payments last year cannot approve them in a projection of
     * today, and pretending otherwise is how a single event turns into permanent
     * power.
     *
     * <p>Authority with no stated start falls back to when it was observed, for
     * the same reason: evidence from August says nothing about January.
     */
    public List<HcinFinancialAuthority> authorities(Instant asOf) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?uri ?holder ?org ?type ?scope ?limit ?currency ?from ?to ?g WHERE {
                  %s
                  GRAPH ?g {
                    ?uri a hcinf:FinancialAuthority ;
                         hcinf:holder ?holder .
                    OPTIONAL { ?uri hcinf:organization  ?org }
                    OPTIONAL { ?uri hcinf:authorityType ?type }
                    OPTIONAL { ?uri hcinf:scope         ?scope }
                    OPTIONAL { ?uri hcinf:spendingLimit ?limit }
                    OPTIONAL { ?uri hcinf:currency      ?currency }
                    OPTIONAL { ?uri hcin:validFrom      ?from }
                    OPTIONAL { ?uri hcin:validTo        ?to }
                    OPTIONAL { ?uri hcin:observedAt     ?obs }
                  }
                  FILTER (COALESCE(?from, ?obs, ?asOf) <= ?asOf)
                  FILTER (!BOUND(?to) || ?to >= ?asOf)
                }
                """.formatted(KNOWLEDGE_GRAPHS);

        return select(query, asOf, solution -> new HcinFinancialAuthority(
                RdfValues.toUri(solution.get("uri")),
                RdfValues.toUri(solution.get("holder")),
                RdfValues.toUri(solution.get("org")),
                AuthorityType.of(RdfValues.toUri(solution.get("type"))),
                RdfValues.toString(solution.get("scope")),
                RdfValues.toDecimal(solution.get("limit")),
                RdfValues.toString(solution.get("currency")),
                RdfValues.toInstant(solution.get("from")),
                RdfValues.toInstant(solution.get("to")),
                KnowledgeTier.of(RdfValues.toUri(solution.get("g")))));
    }

    // -------------------------------------------------------------------------
    // Writing and exporting
    // -------------------------------------------------------------------------

    /**
     * True when the HCIN already holds anything at all about this URI.
     *
     * <p>Used to tell an entity that is being met for the first time from one
     * that is merely being mentioned again.
     */
    public boolean exists(String uri) {
        String query = HcinVocabulary.prefixes() + """
                ASK {
                  %s
                  GRAPH ?g { ?subject ?p ?o }
                }
                """.formatted(KNOWLEDGE_GRAPHS);

        return transactions.read(dataset -> {
            try (QueryExecution execution = QueryExecution.dataset(dataset)
                    .query(query)
                    .substitution("subject", ResourceFactory.createResource(uri))
                    .build()) {
                return execution.execAsk();
            }
        });
    }

    /**
     * Adds statements to one named graph.
     *
     * <p>One statement is treated specially. {@code recordedAt} says when a fact
     * first entered the HCIN, so a later merge of the same fact must not move it
     * forward: without that rule, re-reconciling a thought would add a new
     * timestamp every time and nothing would ever be idempotent.
     */
    public void add(String graphUri, Model statements) {
        transactions.write(dataset -> {
            Model graph = dataset.getNamedModel(graphUri);

            Model incoming = ModelFactory.createDefaultModel().add(statements);
            incoming.listStatements(null, HcinVocabulary.RECORDED_AT, (org.apache.jena.rdf.model.RDFNode) null)
                    .toList().stream()
                    .filter(statement -> graph.contains(statement.getSubject(), HcinVocabulary.RECORDED_AT))
                    .forEach(incoming::remove);

            graph.add(incoming);
        });
    }

    /**
     * Empties one named graph.
     *
     * <p>Wholesale replacement of a graph is a real operation: the schema is
     * reloaded this way on every start, so that a term dropped from a vocabulary
     * disappears from the dataset instead of living on forever. It is not how
     * facts are corrected, which is done by adding a better-founded statement in
     * the graph it belongs to.
     */
    public void clear(String graphUri) {
        transactions.write(dataset -> dataset.getNamedModel(graphUri).removeAll());
    }

    /** How many statements a named graph holds. */
    public long size(String graphUri) {
        return transactions.read(dataset -> dataset.getNamedModel(graphUri).size());
    }

    /**
     * One named graph as Turtle.
     *
     * <p>Serialized from a detached copy: writing sets prefixes on the model,
     * which a read transaction is not allowed to do to the stored one.
     */
    public String export(String graphUri) {
        return write(copyOf(graphUri));
    }

    /**
     * The whole dataset as Turtle, schema included.
     *
     * <p>Flattened into one model on purpose: this is for reading and for
     * debugging, and the graph a statement lives in is already visible in the
     * per-graph export.
     */
    public String exportAll() {
        return transactions.read(dataset -> {
            Model union = ModelFactory.createDefaultModel();
            HcinGraphs.all().forEach(graph -> union.add(dataset.getNamedModel(graph)));
            return write(union);
        });
    }

    /** A snapshot copy of one named graph, safe to use after the transaction ends. */
    public Model copyOf(String graphUri) {
        return transactions.read(dataset ->
                ModelFactory.createDefaultModel().add(dataset.getNamedModel(graphUri)));
    }

    private String write(Model model) {
        model.setNsPrefix("hcin", HcinVocabulary.NS);
        model.setNsPrefix("hcinf", HcinVocabulary.FIN_NS);
        StringWriter out = new StringWriter();
        RDFDataMgr.write(out, model, Lang.TURTLE);
        return out.toString();
    }

    /**
     * Runs a SELECT with the ego, and optionally the instant being asked about,
     * bound into it.
     */
    private <T> List<T> select(String query, Instant asOf, Function<QuerySolution, T> mapper) {
        return transactions.read(dataset -> {
            List<T> results = new ArrayList<>();
            try (QueryExecution execution = build(dataset, query, asOf)) {
                ResultSet rows = execution.execSelect();
                while (rows.hasNext()) {
                    results.add(mapper.apply(rows.next()));
                }
            }
            return results;
        });
    }

    private QueryExecution build(Dataset dataset, String query, Instant asOf) {
        var builder = QueryExecution.dataset(dataset)
                .query(query)
                .substitution("ego", ResourceFactory.createResource(hcin.ego()));

        if (asOf != null) {
            builder = builder.substitution("asOf", RdfValues.dateTime(asOf));
        }
        return builder.build();
    }
}
