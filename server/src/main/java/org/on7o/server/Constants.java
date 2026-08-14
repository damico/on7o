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

    String PROMPT_QTHOUGHT =
        "You are an ontology engineer reviewing a raw thought ontology (rThought).\n"
        + "Identify everything that CANNOT be resolved from the text alone:\n"
        + "- Unresolved entity references (who or what is X?)\n"
        + "- Ambiguous predicates (what does 'do it' mean here?)\n"
        + "- Missing context: time, place, participants\n"
        + "- Implicit assumptions that need user confirmation\n\n"
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

    String PROMPT_CTHOUGHT =
        "You are an ontology engineer. You receive:\n"
        + "- rThought: raw OWL Turtle extracted from a speech transcription\n"
        + "- qThought: clarification questions as OWL Turtle\n"
        + "- User answers: plain-language answers, one per question\n\n"
        + "Produce a consolidated OWL 2 Turtle ontology (cThought) that:\n"
        + "- Resolves all entity references using the answers\n"
        + "- Establishes typed owl:ObjectProperty and owl:DatatypeProperty links\n"
        + "- Assigns owl:Class types to all entities\n"
        + "- Tags every statement with:\n"
        + "    on7o:knowledgeStatus one of (on7o:Asserted | on7o:Inferred | on7o:Hypothesized)\n"
        + "    on7o:confidence a decimal in [0.0, 1.0]\n"
        + "- Emits on7o:ClarificationQuestion for anything the user left unanswered\n\n"
        + "Use prefix: @prefix on7o: <http://on7o.io/ontology#> .\n"
        + "Return ONLY valid Turtle RDF. No prose, no markdown fences.";
}
