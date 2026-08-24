package org.on7o.server.hcin;

/**
 * Which way money moved, read from the ego's point of view.
 */
public enum FlowDirection {

    /** Money entering the ego's financial sphere. */
    INFLOW,

    /** Money leaving the ego's financial sphere. */
    OUTFLOW;

    /** Reads a direction from its vocabulary URI, defaulting to none. */
    public static FlowDirection of(String uri) {
        if (HcinVocabulary.INFLOW.getURI().equals(uri)) {
            return INFLOW;
        }
        if (HcinVocabulary.OUTFLOW.getURI().equals(uri)) {
            return OUTFLOW;
        }
        return null;
    }
}
