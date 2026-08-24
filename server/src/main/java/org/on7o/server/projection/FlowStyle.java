package org.on7o.server.projection;

/**
 * How an edge is drawn, as a projection convention rather than a fact.
 *
 * <p>The distinction the style carries is real: whether money passes directly
 * between the ego and the other end. The choice of dashes to say so is not, and
 * lives here rather than in the ontology for that reason.
 */
public enum FlowStyle {

    /** Money passes directly between these two. */
    SOLID,

    /** A relationship with no direct financial flow. */
    DASHED
}
