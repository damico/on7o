package org.on7o.server.hcin;

import java.util.List;

/**
 * The named graphs the HCIN dataset is divided into.
 *
 * <p>The split is epistemic, not technical. What the vocabulary says, what the
 * user confirmed, what the system inferred and what it merely suspects are
 * different kinds of claim, and a query that must not treat a guess as a fact
 * needs to be able to say so.
 */
public final class HcinGraphs {

    /** Vocabularies and shapes: what the terms mean. */
    public static final String SCHEMA = "urn:hcin:schema";

    /** Facts the user stated or confirmed. */
    public static final String ASSERTED = "urn:hcin:asserted";

    /** Facts derived from other facts by the system. */
    public static final String INFERRED = "urn:hcin:inferred";

    /** Candidate facts that nobody has confirmed. */
    public static final String HYPOTHESES = "urn:hcin:hypotheses";

    /** The thoughts knowledge came from. */
    public static final String THOUGHTS = "urn:hcin:thoughts";

    /** Clarification questions, as knowledge about what is not known. */
    public static final String QUESTIONS = "urn:hcin:questions";

    /** Who observed what, when, and from which evidence. */
    public static final String PROVENANCE = "urn:hcin:provenance";

    private static final List<String> ALL = List.of(
            SCHEMA, ASSERTED, INFERRED, HYPOTHESES, THOUGHTS, QUESTIONS, PROVENANCE);

    private HcinGraphs() {
    }

    /** Every named graph, in a stable order. */
    public static List<String> all() {
        return ALL;
    }

    /** True when the given URI is one of the HCIN graphs. */
    public static boolean isKnown(String uri) {
        return ALL.contains(uri);
    }

    /**
     * Resolves a short name such as {@code asserted} to its graph URI.
     *
     * @param name short name or full URI
     * @return the graph URI
     * @throws IllegalArgumentException when the name matches no graph
     */
    public static String resolve(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("graph name is required");
        }
        if (isKnown(name)) {
            return name;
        }
        String candidate = "urn:hcin:" + name.trim().toLowerCase();
        if (isKnown(candidate)) {
            return candidate;
        }
        throw new IllegalArgumentException("unknown graph: " + name);
    }
}
