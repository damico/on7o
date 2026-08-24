package org.on7o.server.api;

/**
 * Options for analyzing a thought. The body is optional: posting nothing means
 * accepting the defaults.
 *
 * @param force re-run the model even when the thought already has its artifacts
 */
public record ThoughtAnalysisRequest(Boolean force) {

    /** The defaults, for a request that carried no body. */
    public static ThoughtAnalysisRequest defaults() {
        return new ThoughtAnalysisRequest(false);
    }

    /** Whether to re-run the model, defaulting to no. */
    public boolean forced() {
        return Boolean.TRUE.equals(force);
    }
}
