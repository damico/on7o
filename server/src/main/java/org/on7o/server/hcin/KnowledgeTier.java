package org.on7o.server.hcin;

/**
 * How strongly a fact read out of the HCIN is believed, told by the graph it
 * was found in.
 *
 * <p>Carried on every record the repository returns, so that a consumer never
 * has to treat a hypothesis and a confirmed fact as the same thing without
 * knowing it is doing so.
 */
public enum KnowledgeTier {

    /** Stated or confirmed by a human. */
    ASSERTED,

    /** Derived by the system from other facts. */
    INFERRED,

    /** Proposed by the system and confirmed by nobody. */
    HYPOTHESIZED;

    /** The tier a named graph represents. */
    public static KnowledgeTier of(String graphUri) {
        return switch (graphUri) {
            case HcinGraphs.ASSERTED -> ASSERTED;
            case HcinGraphs.INFERRED -> INFERRED;
            default -> HYPOTHESIZED;
        };
    }

    /** The graph facts of this tier belong in. */
    public String graph() {
        return switch (this) {
            case ASSERTED -> HcinGraphs.ASSERTED;
            case INFERRED -> HcinGraphs.INFERRED;
            case HYPOTHESIZED -> HcinGraphs.HYPOTHESES;
        };
    }
}
