package org.on7o.server.api;

/**
 * Options for consolidating a thought. The body is optional: posting nothing
 * means accepting the defaults.
 *
 * @param force           re-run the model even when a consolidated artifact exists
 * @param allowIncomplete consolidate even with required questions unanswered
 */
public record ConsolidationRequest(Boolean force, Boolean allowIncomplete) {

    /** The defaults, for a request that carried no body. */
    public static ConsolidationRequest defaults() {
        return new ConsolidationRequest(false, false);
    }

    /** Whether to re-run the model, defaulting to no. */
    public boolean forced() {
        return Boolean.TRUE.equals(force);
    }

    /** Whether to proceed without the required answers, defaulting to no. */
    public boolean incompleteAllowed() {
        return Boolean.TRUE.equals(allowIncomplete);
    }
}
