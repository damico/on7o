package org.on7o.server.analysis;

/**
 * The outcome of consolidating a thought.
 */
public enum ConsolidationStatus {

    /** The thought was consolidated into a knowledge artifact. */
    CONSOLIDATED,

    /**
     * Required questions are still unanswered, so nothing was consolidated.
     * The caller may answer them, or ask again allowing an incomplete result.
     */
    MISSING_REQUIRED_ANSWERS
}
