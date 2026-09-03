package org.on7o.server;

/**
 * Application-wide constants: configuration keys, default values, and LLM prompts.
 *
 * <p>Interface fields are implicitly {@code public static final} in Java.
 * All string literals here are intentionally immutable and referenced by name
 * throughout the codebase to prevent scattered magic values.
 */
public interface Constants {

    // -------------------------------------------------------------------------
    // OpenAI
    // -------------------------------------------------------------------------

    /** Environment variable that carries the OpenAI API key. */
    String ENV_OPENAI_KEY = "OPENAI_API_KEY";

    /** Environment variable that overrides the model name. */
    String ENV_OPENAI_MODEL = "OPENAI_MODEL";

    /** Default reasoning model used when {@link #ENV_OPENAI_MODEL} is not set. */
    String DEFAULT_MODEL = "o3";

    /** OpenAI chat completions endpoint. */
    String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    // -------------------------------------------------------------------------
    // Ontology
    // -------------------------------------------------------------------------

    /** Base URI for all on7o ontology terms. */
    String ONTOLOGY_BASE = "http://on7o.io/ontology#";

    /** Standard Turtle prefix declaration used in all generated ontologies. */
    String TURTLE_PREFIX = "@prefix on7o: <" + ONTOLOGY_BASE + "> .";

    // -------------------------------------------------------------------------
    // Stage 1 - rThought: raw semantic extraction
    // -------------------------------------------------------------------------

    String PROMPT_RTHOUGHT =
        "You are an ontology engineer. Analyze the given speech transcription and extract "
        + "its semantic components strictly from the text.\n\n"
        + "Identify and express in OWL 2 Turtle (text/turtle):\n"
        + "- Subjects: people, places, things, events, concepts explicitly named\n"
        + "- Predicates: actions and relationships expressed by verbs\n"
        + "- Adjectives and attributes: descriptors attached to entities\n"
        + "- Temporal and spatial context when stated\n\n"
        + "Rules:\n"
        + "- Do NOT infer beyond what the text explicitly states.\n"
        + "- Do NOT add external knowledge.\n"
        + "- Tag every triple with rdfs:comment quoting the source words.\n"
        + "- Use prefix: @prefix on7o: <http://on7o.io/ontology#> .\n\n"
        + "Return ONLY valid Turtle RDF. No prose, no markdown fences.";

    // -------------------------------------------------------------------------
    // Stage 2 - qThought: clarification questions as ontology
    // -------------------------------------------------------------------------

    /** Maximum number of clarification questions the qThought stage may produce. */
    int QTHOUGHT_MAX_QUESTIONS = 20;

    String PROMPT_QTHOUGHT =
        "You are an ontology engineer reviewing a raw thought ontology (rThought).\n"
        + "Identify everything that CANNOT be resolved from the text alone:\n"
        + "- Unresolved entity references (who or what is X?)\n"
        + "- Ambiguous predicates (what does 'do it' mean here?)\n"
        + "- Missing context: time, place, participants\n"
        + "- Implicit assumptions that need user confirmation\n\n"
        + "Ask at most " + QTHOUGHT_MAX_QUESTIONS + " questions in total. Prioritize questions "
        + "that ground the thought:\n"
        + "1. Why - the motivation or reason behind the thought\n"
        + "2. What / Who - the subject, object, or participants involved\n"
        + "3. When - the temporal context, only if the thought relates to time\n"
        + "Rank questions by this priority and drop the least important ones if the limit "
        + "of " + QTHOUGHT_MAX_QUESTIONS + " would otherwise be exceeded.\n\n"
        + "Output exactly two sections, in this order:\n\n"
        + "1. A JSON block fenced with ```json and ``` containing:\n"
        + "   {\"questions\": [\"Question 1?\", \"Question 2?\", ...]}\n"
        + "   These are plain-language questions addressed to the user.\n\n"
        + "2. A Turtle block fenced with ```turtle and ``` containing OWL 2 Turtle\n"
        + "   where each question is an on7o:ClarificationQuestion instance\n"
        + "   linked via on7o:concerns to the unresolved entity or triple.\n"
        + "   Use prefix: @prefix on7o: <http://on7o.io/ontology#> .\n\n"
        + "Do not invent answers. Only ask.";

    // -------------------------------------------------------------------------
    // Stage 3 - cThought: consolidated ontology
    // -------------------------------------------------------------------------

    /** URI of the speaker, the person every thought is told from the point of view of. */
    String EGO_URI = "urn:hcin:person:me";

    /**
     * Asks the consolidation stage to write the parts the HCIN understands in
     * the HCIN's own terms.
     *
     * <p>Reconciliation reads people, organizations and claims out of any
     * vocabulary, but an interaction, a payment or an authority carries more
     * structure than a triple: a time, an amount, a currency, a direction, a
     * scope. Those only survive the merge if they arrive already shaped, so this
     * says exactly which shapes to use.
     *
     * <p>The instruction is additive. A thought with no meeting and no money in
     * it produces none of these and is consolidated exactly as before.
     */
    String HCIN_MAPPING =
        "Additionally, whenever the thought mentions one of the following, express it using the "
        + "HCIN vocabulary, in the same Turtle output:\n"
        + "@prefix hcin:  <http://on7o.io/hcin#> .\n"
        + "@prefix hcinf: <http://on7o.io/hcin/financial#> .\n"
        + "@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n\n"
        + "- A person:        a hcin:Person, with hcin:label naming them.\n"
        + "- An organization: a hcin:Organization, with hcin:label naming it.\n"
        + "- Belonging:       a hcin:Membership with hcin:member, hcin:memberOf, "
        + "optional hcin:role, optional hcin:validFrom and hcin:validTo.\n"
        + "- Any meeting, call, message or mention between the speaker and someone else: "
        + "a hcin:Interaction with hcin:participant <" + EGO_URI + "> and the other person, "
        + "hcin:occurredAt as an xsd:dateTime, and hcin:interactionType as one of "
        + "\"meeting\", \"strategicMeeting\", \"phoneCall\", \"message\", \"mention\", "
        + "\"financialNegotiation\" or \"financialTransaction\".\n"
        + "- Money moving:    a hcinf:FinancialFlow with hcinf:flowSource, hcinf:flowTarget, "
        + "hcinf:direction (hcinf:Inflow when money reaches the speaker, hcinf:Outflow when it "
        + "leaves), hcinf:amount as an xsd:decimal, hcinf:currency as an ISO 4217 code, and "
        + "hcin:occurredAt.\n"
        + "- Power over money: a hcinf:FinancialAuthority with hcinf:holder, hcinf:organization, "
        + "hcinf:authorityType (hcinf:ExpenditureAuthority or hcinf:RevenueAuthority), and "
        + "hcinf:scope ONLY when the user actually said what it covers.\n\n"
        + "Rules for this part:\n"
        + "- The speaker is always <" + EGO_URI + ">.\n"
        + "- Never invent a scope, an amount, a currency or a date. Leaving a field out is "
        + "correct when the thought does not say it; inventing one is not.\n"
        + "- One approval is one approval. Do not describe it as standing or unrestricted "
        + "authority, and do not give it an end date the user did not state.\n"
        + "- Mark with hcin:knowledgeStatus hcin:Asserted only what the user stated or confirmed.\n\n";

    String PROMPT_CTHOUGHT =
        "You are an ontology engineer. You receive:\n"
        + "- rThought: raw OWL Turtle extracted from a speech transcription\n"
        + "- qThought: clarification questions as OWL Turtle\n"
        + "- User answers: plain-language answers, one per question\n\n"
        + "Produce a consolidated OWL 2 Turtle ontology (cThought) that:\n"
        + "- Resolves all entity references using the answers\n"
        + "- Establishes typed owl:ObjectProperty and owl:DatatypeProperty links\n"
        + "- Assigns owl:Class types to all entities\n"
        + "- Says how every statement is known, as an annotation of the statement "
        + "itself. Write the triple, then annotate it with RDF-star:\n\n"
        + "      on7o:Bob on7o:worksAt on7o:AcmeCompany .\n"
        + "      << on7o:Bob on7o:worksAt on7o:AcmeCompany >>\n"
        + "              on7o:knowledgeStatus on7o:Asserted ;\n"
        + "              on7o:confidence      0.9 .\n\n"
        + "  on7o:knowledgeStatus is on7o:Asserted when the user stated or confirmed it, "
        + "on7o:Inferred when you worked it out from what they said, and "
        + "on7o:Hypothesized when it is a guess. on7o:confidence is a decimal in "
        + "[0.0, 1.0].\n"
        + "  Never put on7o:knowledgeStatus or on7o:confidence on an entity. An entity is "
        + "not a claim: it is the statement that is asserted, inferred or guessed, and a "
        + "tag on the entity says nothing about which of its statements you believe.\n"
        + "- Emits on7o:ClarificationQuestion for anything the user left unanswered\n\n"
        + HCIN_MAPPING
        + "Use prefix: @prefix on7o: <http://on7o.io/ontology#> .\n"
        + "Return ONLY valid Turtle RDF. No prose, no markdown fences.";

    // -------------------------------------------------------------------------
    // Entity-derived thoughts: independent eThought/cThought pairs seeded by a
    // single entity that appeared in a cThought, not by a raw transcription.
    // -------------------------------------------------------------------------

    /** Maximum number of entities automatically derived from one cThought. */
    int MAX_AUTO_DERIVED_ENTITIES = 5;

    /** Maximum number of clarification questions the eThought stage may produce. */
    int ETHOUGHT_MAX_QUESTIONS = 5;

    String PROMPT_ENTITY_SCAN =
        "You are an ontology engineer reviewing a consolidated thought ontology (cThought).\n"
        + "Find entities that are referenced or used to type/qualify something in this "
        + "ontology but have no triples of their own explaining what they are (for example, "
        + "a role or occupation attached to a named individual, such as a profession used to "
        + "qualify a person's name).\n"
        + "Only pick entities whose meaning is not self-evident from their label alone.\n"
        + "Ignore anything that belongs to the machinery rather than to the world the "
        + "thought is about: epistemic and provenance vocabulary such as Asserted, "
        + "Inferred, Hypothesized, knowledgeStatus, confidence, observedAt or recordedAt "
        + "is defined elsewhere and is never a candidate.\n\n"
        + "Each \"label\" MUST name exactly one bare concept, e.g. \"Psychiatrist\". Never "
        + "combine an individual with its type into one label, e.g. never "
        + "\"Dr_Rubens : Psychiatrist\" or \"Dr_Rubens (Psychiatrist)\". If a role or type "
        + "used to qualify a named individual is what needs defining, return only that role "
        + "or type's own name, not the individual it qualifies.\n\n"
        + "Output ONLY a JSON block fenced with ```json and ``` containing:\n"
        + "   {\"entities\": [{\"label\": \"Psychiatrist\", "
        + "\"reason\": \"used to qualify Dr. Rubens but never itself defined\"}, ...]}\n"
        + "Return at most " + MAX_AUTO_DERIVED_ENTITIES + " entities, most important first. "
        + "Return {\"entities\": []} if none qualify.";

    String PROMPT_ETHOUGHT =
        "You are an ontology engineer. A single entity below was referenced in a thought "
        + "ontology but was never itself defined. Generate clarification questions that would "
        + "let a user explain what this entity is, independent of any other thought.\n\n"
        + "Output exactly two sections, in this order:\n\n"
        + "1. A JSON block fenced with ```json and ``` containing:\n"
        + "   {\"questions\": [\"Question 1?\", \"Question 2?\", ...]}\n"
        + "   Ask at most " + ETHOUGHT_MAX_QUESTIONS + " plain-language questions that ground "
        + "the entity: why it matters here, what/who it is, and, only if the entity relates "
        + "to time, when it applies.\n\n"
        + "2. A Turtle block fenced with ```turtle and ``` containing OWL 2 Turtle where each "
        + "question is an on7o:ClarificationQuestion instance linked via on7o:concerns to the "
        + "entity.\n"
        + "   Use prefix: @prefix on7o: <http://on7o.io/ontology#> .\n\n"
        + "Do not invent answers. Only ask.";

    String PROMPT_ECTHOUGHT =
        "You are an ontology engineer. You receive:\n"
        + "- eThought: clarification questions about a single entity, as OWL Turtle\n"
        + "- User answers: plain-language answers, one per question\n\n"
        + "Produce a standalone, consolidated OWL 2 Turtle ontology (cThought) that defines "
        + "this entity from the answers alone. This ontology is independent of any other "
        + "thought: do not assume or reference any external context beyond what the answers "
        + "state.\n"
        + "- Assign owl:Class or the appropriate owl:ObjectProperty/owl:DatatypeProperty types\n"
        + "- Says how every statement is known, as an annotation of the statement "
        + "itself. Write the triple, then annotate it with RDF-star:\n\n"
        + "      on7o:Bob on7o:worksAt on7o:AcmeCompany .\n"
        + "      << on7o:Bob on7o:worksAt on7o:AcmeCompany >>\n"
        + "              on7o:knowledgeStatus on7o:Asserted ;\n"
        + "              on7o:confidence      0.9 .\n\n"
        + "  on7o:knowledgeStatus is on7o:Asserted when the user stated or confirmed it, "
        + "on7o:Inferred when you worked it out from what they said, and "
        + "on7o:Hypothesized when it is a guess. on7o:confidence is a decimal in "
        + "[0.0, 1.0].\n"
        + "  Never put on7o:knowledgeStatus or on7o:confidence on an entity. An entity is "
        + "not a claim: it is the statement that is asserted, inferred or guessed, and a "
        + "tag on the entity says nothing about which of its statements you believe.\n"
        + "- Emits on7o:ClarificationQuestion for anything the user left unanswered\n\n"
        + "Use prefix: @prefix on7o: <http://on7o.io/ontology#> .\n"
        + "Return ONLY valid Turtle RDF. No prose, no markdown fences.";
}
