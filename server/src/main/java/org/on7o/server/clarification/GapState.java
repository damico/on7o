package org.on7o.server.clarification;

/**
 * Where one gap in the network stands as a question.
 *
 * <p>A validation report lists what the shapes found, which is a true account of
 * the data and a poor account of what the reader can do about it. Some gaps have
 * already become questions, some are waiting for the next run, and some belong to
 * a node no thought is recorded as having observed. Showing all of them as one
 * undifferentiated list invites a reader to look for an answer box that is not
 * there.
 */
public enum GapState {

    /**
     * No thought is recorded as having observed the node, so there is no
     * conversation to ask in.
     */
    UNATTRIBUTED,

    /** Askable, and not yet asked. The next run over the network will ask it. */
    WAITING,

    /** Asked, and still open in the thought it belongs to. */
    ASKED,

    /** Asked and dealt with, either answered or deliberately skipped. */
    CLOSED
}
