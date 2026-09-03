package org.on7o.server.clarification;

/**
 * How a question expects to be answered.
 *
 * <p>Most clarification questions are open: the qThought stage asks what a
 * thought left unresolved, and the answer is whatever the user has to say. A
 * question the network raised about itself is different. It asks for one
 * property of one node, and that property often has a small set of values the
 * ontology already declares or the network has already used. Handing the user a
 * blank box for such a question is asking them to retype something the system
 * knows, and to spell it the same way it was spelled before.
 *
 * <p>The kind changes only how the question is presented. Whatever the user
 * picks becomes the same answer text every other kind produces, so nothing
 * downstream needs to know that a button was involved.
 */
public enum AnswerKind {

    /** A blank box. Anything the user wants to say. */
    TEXT,

    /** One value out of a set the ontology closes. */
    CHOICE,

    /**
     * One or more values out of that set.
     *
     * <p>A tie can be professional and financial at once, and forcing a single
     * layer on it would discard the multiplexity the relational model exists to
     * represent.
     */
    MULTI_CHOICE,

    /**
     * A box with what the network already knows offered beside it.
     *
     * <p>For properties whose values are open, such as which contract or project
     * a statement belongs to. Picking an existing one keeps the same setting from
     * being minted twice under two spellings; typing a new one is always allowed,
     * because the network cannot have seen every context there is.
     */
    SUGGESTION,

    /**
     * The same, with more than one value allowed.
     *
     * <p>A statement can belong to a project and to the organization the project
     * runs under at once, and making the user choose between them would lose one
     * of the two settings the question was asked to learn.
     */
    MULTI_SUGGESTION,

    /** A calendar date. */
    DATE,

    /** A number. */
    NUMBER;

    /** True when the network should be asked what this question could offer. */
    public boolean offersOptions() {
        return this == CHOICE || this == MULTI_CHOICE
                || this == SUGGESTION || this == MULTI_SUGGESTION;
    }

    /** True when more than one option can be picked at once. */
    public boolean isMultiple() {
        return this == MULTI_CHOICE || this == MULTI_SUGGESTION;
    }
}
