package org.on7o.server.projection;

/**
 * What a node in the projection stands for.
 */
public enum NodeType {

    /** The person the whole projection is drawn from. */
    EGO,

    /** Anyone else. */
    PERSON,

    /**
     * An organization the ego relates to directly, by money or by interaction.
     *
     * <p>Organizations are normally the setting a relationship happens in, and
     * appear as a group around their people. One only becomes a node of its own
     * when the ego deals with the organization itself rather than with someone
     * inside it: a payment to ACME is a fact about ACME.
     */
    ORGANIZATION
}
