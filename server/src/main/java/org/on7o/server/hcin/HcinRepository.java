package org.on7o.server.hcin;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** How many options a question may offer before the offer stops being one. */
    private static final int MOST_OPTIONS = 24;

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

    /** The name the ego is known by, which is how a thought naming them is recognized. */
    public String egoLabel() {
        return hcin.egoLabel();
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
     * Which knowledge graph holds a node.
     *
     * <p>An answer completing a statement belongs in the graph the statement is
     * in. A layer written into the asserted graph, about a relationship living
     * among the hypotheses, would be a statement split across two tiers: the
     * shapes validate one graph at a time and would go on reporting the property
     * as missing, which is exactly what the answer was given to settle.
     *
     * @return the graph URI, or null when no knowledge graph mentions the node
     */
    public String graphOf(String uri) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?g WHERE {
                  %s
                  GRAPH ?g { ?subject ?p ?o }
                }
                LIMIT 1
                """.formatted(KNOWLEDGE_GRAPHS);

        return transactions.read(dataset -> {
            try (QueryExecution execution = QueryExecution.dataset(dataset)
                    .query(query)
                    .substitution("subject", ResourceFactory.createResource(uri))
                    .build()) {
                ResultSet results = execution.execSelect();
                return results.hasNext() ? results.next().getResource("g").getURI() : null;
            }
        });
    }

    /**
     * The URI of whatever the network already calls by this name.
     *
     * <p>Answering by picking what was offered has to land on the node that was
     * offered, not on a second node carrying the same name. Labels are matched
     * exactly, since the offer was made in the network's own words.
     *
     * @return the URI, or null when nothing carries that label
     */
    public String uriLabelled(String label) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?subject WHERE {
                  VALUES ?g { <%s> <%s> <%s> <%s> }
                  GRAPH ?g { ?subject hcin:label|rdfs:label ?label }
                }
                LIMIT 1
                """.formatted(HcinGraphs.SCHEMA, HcinGraphs.ASSERTED,
                        HcinGraphs.INFERRED, HcinGraphs.HYPOTHESES);

        return transactions.read(dataset -> {
            try (QueryExecution execution = QueryExecution.dataset(dataset)
                    .query(query)
                    .substitution("label", ResourceFactory.createStringLiteral(label))
                    .build()) {
                ResultSet results = execution.execSelect();
                return results.hasNext() ? results.next().getResource("subject").getURI() : null;
            }
        });
    }

    /**
     * The terms of other vocabularies this model declares itself equal to.
     *
     * <p>{@code hcin-core.ttl} says that {@code hcin:Membership} is
     * {@code org:Membership} and that {@code hcin:wasDerivedFrom} is
     * {@code prov:wasDerivedFrom}. This reads those axioms back so that a
     * consolidated thought written in the standard terms can be understood
     * rather than discarded: the ontology decides what is a synonym, and no list
     * in the code has to be kept in step with it.
     *
     * <p>Only equivalences are read. A subproperty is not a synonym:
     * {@code hcin:label} is an {@code rdfs:label}, but not every
     * {@code rdfs:label} is the name the ego uses, and rewriting one into the
     * other would rename classes.
     *
     * @return each foreign term mapped to the HCIN term it equals
     */
    public Map<String, String> vocabularyAliases() {
        String query = HcinVocabulary.prefixes() + """
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT DISTINCT ?other ?term WHERE {
                  GRAPH <%s> {
                    { ?term owl:equivalentClass    ?other }
                    UNION { ?term owl:equivalentProperty ?other }
                    UNION { ?other owl:equivalentClass    ?term }
                    UNION { ?other owl:equivalentProperty ?term }
                  }
                  FILTER (isIRI(?other) && isIRI(?term))
                  FILTER (STRSTARTS(STR(?term), "%s"))
                  FILTER (!STRSTARTS(STR(?other), "%s"))
                }
                """.formatted(HcinGraphs.SCHEMA, HcinVocabulary.NS, HcinVocabulary.NS);

        return transactions.read(dataset -> {
            Map<String, String> aliases = new LinkedHashMap<>();
            try (QueryExecution execution = QueryExecution.dataset(dataset).query(query).build()) {
                ResultSet results = execution.execSelect();
                while (results.hasNext()) {
                    QuerySolution row = results.next();
                    aliases.putIfAbsent(row.getResource("other").getURI(),
                            row.getResource("term").getURI());
                }
            }
            return aliases;
        });
    }

    /**
     * Whether the vocabulary already says what a name means.
     *
     * <p>The pipeline reviews a consolidated thought for terms it used without
     * defining, and offers them back as things worth a thought of their own. Its
     * own machinery reads exactly like such a term: a thought is stamped
     * {@code knowledgeStatus Asserted} and never explains what Asserted is,
     * because the explanation lives in {@code hcin-core.ttl} rather than in the
     * thought. Asking the user to define it is asking them about the tool.
     *
     * <p>Matched on the term's name and on its label, ignoring case and
     * punctuation, since a scan reports whatever spelling it read.
     *
     * @param name a term as the scan named it
     */
    public boolean definesTerm(String name) {
        String key = key(name);
        if (key.isEmpty()) {
            return false;
        }

        String query = HcinVocabulary.prefixes() + """
                ASK {
                  {
                    GRAPH <%s> { ?term rdfs:label ?label }
                    FILTER (LCASE(REPLACE(STR(?label), "[^A-Za-z0-9]", "")) = ?key)
                  } UNION {
                    GRAPH <%s> { ?term a ?type }
                    FILTER (LCASE(REPLACE(STRAFTER(STR(?term), "#"), "[^A-Za-z0-9]", "")) = ?key)
                  }
                }
                """.formatted(HcinGraphs.SCHEMA, HcinGraphs.SCHEMA);

        return transactions.read(dataset -> {
            try (QueryExecution execution = QueryExecution.dataset(dataset)
                    .query(query)
                    .substitution("key", ResourceFactory.createStringLiteral(key))
                    .build()) {
                return execution.execAsk();
            }
        });
    }

    /** A name reduced to what matching should care about: lower case, letters and digits. */
    private static String key(String name) {
        return name == null ? "" : name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Every thought recorded as having observed a node, earliest first.
     *
     * <p>{@link #observingThought} answers a different question: which
     * conversation a node belongs to, so a gap in it can be asked about there.
     * This one is for a reader looking at a person on a picture and wanting to
     * know where that person came from, which is every thought that mentioned
     * them rather than only the first.
     */
    public List<String> thoughtsMentioning(String uri) {
        String query = HcinVocabulary.prefixes() + """
                SELECT DISTINCT ?thoughtId ?observedAt WHERE {
                  GRAPH <%s> {
                    ?observation a hcin:Observation ;
                                 hcin:about ?subject ;
                                 hcin:thoughtId ?thoughtId .
                    OPTIONAL { ?observation hcin:observedAt ?observedAt }
                  }
                }
                ORDER BY ?observedAt ?thoughtId
                """.formatted(HcinGraphs.PROVENANCE);

        return transactions.read(dataset -> {
            List<String> thoughts = new ArrayList<>();
            try (QueryExecution execution = QueryExecution.dataset(dataset)
                    .query(query)
                    .substitution("subject", ResourceFactory.createResource(uri))
                    .build()) {
                ResultSet results = execution.execSelect();
                while (results.hasNext()) {
                    String id = results.next().getLiteral("thoughtId").getString();
                    if (!thoughts.contains(id)) {
                        thoughts.add(id);
                    }
                }
            }
            return thoughts;
        });
    }

    /**
     * The thought a node was first observed in, or null when nothing recorded it.
     *
     * <p>This is what makes a gap in the network answerable. A node has no
     * conversation attached to it, but the thought that produced it does, so a
     * question about the node can join the questions that thought already asks
     * and travel through the same lifecycle rather than needing one of its own.
     *
     * <p>The earliest observation wins. A node mentioned again later still
     * belongs to the conversation that introduced it.
     */
    public String observingThought(String uri) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?thoughtId ?observedAt WHERE {
                  GRAPH <%s> {
                    ?observation a hcin:Observation ;
                                 hcin:about ?subject ;
                                 hcin:thoughtId ?thoughtId .
                    OPTIONAL { ?observation hcin:observedAt ?observedAt }
                  }
                }
                ORDER BY ?observedAt
                LIMIT 1
                """.formatted(HcinGraphs.PROVENANCE);

        return transactions.read(dataset -> {
            try (QueryExecution execution = QueryExecution.dataset(dataset)
                    .query(query)
                    .substitution("subject", ResourceFactory.createResource(uri))
                    .build()) {
                ResultSet results = execution.execSelect();
                return results.hasNext() ? results.next().getLiteral("thoughtId").getString() : null;
            }
        });
    }

    /**
     * What a property is allowed to say, according to the network itself.
     *
     * <p>Three sources, and all three are needed. The ontology declares the range
     * of some properties as a class with named individuals, and those are the
     * whole answer set: which layer a relationship lives in has exactly the layers
     * {@code hcin-core.ttl} declares. Other properties, {@code hcin:context} among
     * them, have no enumerable range at all; for those the ontology names the
     * classes whose instances are plausible settings, with {@code hcin:suggestFrom},
     * and the network is searched for them. And whatever a property has already
     * been used to say is always worth offering again.
     *
     * <p>Nothing here is invented. An empty list means the network has never
     * seen a value for this property and has nothing to offer, which is a true
     * statement about the network rather than a reason to make something up.
     *
     * <p>Values are returned as labels, since a URI is not an answer a person
     * can pick. The list is capped: a property whose range turned out to be a
     * populous class would otherwise offer hundreds of choices, which is not an
     * offer.
     *
     * @param property URI of the property being asked about
     * @return distinct values, labelled, at most {@value #MOST_OPTIONS} of them
     */
    public List<String> knownValuesOf(String property) {
        String query = HcinVocabulary.prefixes() + """
                SELECT DISTINCT ?value ?label WHERE {
                  {
                    GRAPH <%s> {
                      ?property rdfs:range ?range .
                      ?value a ?range .
                    }
                  } UNION {
                    %s
                    GRAPH <%s> { ?property hcin:suggestFrom ?candidate }
                    GRAPH ?g { ?value a ?candidate }
                  } UNION {
                    %s
                    GRAPH ?g { ?statement ?property ?value }
                  }
                  OPTIONAL {
                    VALUES ?h { <%s> <%s> <%s> <%s> }
                    GRAPH ?h { ?value hcin:label|rdfs:label ?label }
                  }
                }
                ORDER BY ?label ?value
                LIMIT %d
                """.formatted(HcinGraphs.SCHEMA,
                        KNOWLEDGE_GRAPHS, HcinGraphs.SCHEMA,
                        KNOWLEDGE_GRAPHS,
                        HcinGraphs.SCHEMA, HcinGraphs.ASSERTED,
                        HcinGraphs.INFERRED, HcinGraphs.HYPOTHESES,
                        MOST_OPTIONS);

        return transactions.read(dataset -> {
            List<String> values = new ArrayList<>();
            try (QueryExecution execution = QueryExecution.dataset(dataset)
                    .query(query)
                    .substitution("property", ResourceFactory.createProperty(property))
                    .build()) {
                ResultSet results = execution.execSelect();
                while (results.hasNext()) {
                    String value = optionOf(results.next());
                    if (value != null && !values.contains(value)) {
                        values.add(value);
                    }
                }
            }
            return values;
        });
    }

    /**
     * One row of {@link #knownValuesOf} as something to show.
     *
     * <p>A label when there is one, the lexical form when the value is a literal,
     * and the local name otherwise. A blank node has no name worth offering and
     * is dropped.
     */
    private static String optionOf(QuerySolution row) {
        if (row.contains("label")) {
            return row.getLiteral("label").getString();
        }
        RDFNode value = row.get("value");
        if (value.isLiteral()) {
            return value.asLiteral().getLexicalForm();
        }
        return value.isURIResource() ? localName(value.asResource().getURI()) : null;
    }

    /**
     * A short human phrase for a node, built from what it is and who it names.
     *
     * <p>A URI is not something to put in a question. An interaction, a
     * membership or a qualified relationship has no label of its own, so it is
     * described by the entities it connects: those do have labels, and they are
     * what the user recognizes.
     *
     * <p>The entities and the statements about them routinely live in different
     * graphs, since a person can be an asserted fact while what they are said to
     * have done is still a hypothesis. The two are therefore matched
     * independently, and a description that required both in the same graph
     * would find nothing at all.
     *
     * @return something like {@code presenter relationship between Ana and Bob},
     *         or the bare local name when the node names nobody
     */
    public String describe(String uri) {
        return transactions.read(dataset -> {
            String kind = kindOf(dataset, uri);
            List<String> labels = namesConnectedTo(dataset, uri);
            return phrase(kind, labels, uri);
        });
    }

    /** What the node is: its relation type when it has one, otherwise its class. */
    private String kindOf(Dataset dataset, String uri) {
        String query = HcinVocabulary.prefixes() + """
                SELECT ?type ?relationType WHERE {
                  %s
                  GRAPH ?g {
                    ?subject a ?type .
                    OPTIONAL { ?subject hcin:relationType ?relationType }
                  }
                }
                LIMIT 1
                """.formatted(KNOWLEDGE_GRAPHS);

        try (QueryExecution execution = QueryExecution.dataset(dataset)
                .query(query)
                .substitution("subject", ResourceFactory.createResource(uri))
                .build()) {
            ResultSet results = execution.execSelect();
            if (!results.hasNext()) {
                return "statement";
            }
            QuerySolution row = results.next();
            String type = humanize(localName(row.getResource("type").getURI()));
            return row.contains("relationType")
                    ? humanize(localName(row.getResource("relationType").getURI())) + " " + type
                    : type;
        }
    }

    /**
     * The labels of the entities a node points at, source and target first.
     *
     * <p>Order is what makes the phrase readable: "between Ana and Bob" says
     * which way a relationship runs, and drawing the names in whatever order the
     * store returned them would silently reverse it.
     */
    private List<String> namesConnectedTo(Dataset dataset, String uri) {
        String query = HcinVocabulary.prefixes() + """
                SELECT DISTINCT ?role ?label WHERE {
                  %s
                  VALUES ?h { <%s> <%s> <%s> }
                  GRAPH ?g { ?subject ?role ?other }
                  GRAPH ?h { ?other hcin:label ?label }
                }
                """.formatted(KNOWLEDGE_GRAPHS,
                        HcinGraphs.ASSERTED, HcinGraphs.INFERRED, HcinGraphs.HYPOTHESES);

        List<String> ordered = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        try (QueryExecution execution = QueryExecution.dataset(dataset)
                .query(query)
                .substitution("subject", ResourceFactory.createResource(uri))
                .build()) {
            ResultSet results = execution.execSelect();
            while (results.hasNext()) {
                QuerySolution row = results.next();
                String label = row.getLiteral("label").getString();
                String role = row.getResource("role").getURI();
                if (role.equals(HcinVocabulary.SOURCE.getURI())) {
                    ordered.add(0, label);
                } else if (role.equals(HcinVocabulary.TARGET.getURI())) {
                    ordered.add(label);
                } else if (!rest.contains(label)) {
                    rest.add(label);
                }
            }
        }
        for (String label : rest) {
            if (!ordered.contains(label)) {
                ordered.add(label);
            }
        }
        return ordered;
    }

    /** "presenter relationship between Ana and Bob", or the local name when nothing is known. */
    private static String phrase(String kind, List<String> labels, String uri) {
        return switch (labels.size()) {
            case 0 -> kind + " " + localName(uri);
            case 1 -> kind + " with " + labels.get(0);
            case 2 -> kind + " between " + labels.get(0) + " and " + labels.get(1);
            default -> kind + " with " + String.join(", ", labels.subList(0, labels.size() - 1))
                    + " and " + labels.get(labels.size() - 1);
        };
    }

    /** "FinancialFlow" becomes "financial flow". */
    private static String humanize(String type) {
        return type.replaceAll("(?<!^)([A-Z])", " $1").toLowerCase();
    }

    /** The part of a URI after its last separator. */
    private static String localName(String uri) {
        int cut = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf(':'));
        return cut < 0 ? uri : uri.substring(cut + 1);
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
