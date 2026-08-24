package org.on7o.server.clarification;

/**
 * Where a clarification question stands.
 *
 * <p>The state is stored rather than derived, because {@link #OBSOLETE} cannot be
 * read off the answers: it is decided when a re-analysis replaces the question.
 */
public enum QuestionStatus {

    /** Asked and still waiting for the user. */
    OPEN,

    /** The user answered it. */
    ANSWERED,

    /** The user was asked and chose not to answer. */
    SKIPPED,

    /** Superseded by a re-analysis of the same thought, and no longer asked. */
    OBSOLETE
}
