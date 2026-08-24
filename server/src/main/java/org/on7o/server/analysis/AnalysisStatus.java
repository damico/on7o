package org.on7o.server.analysis;

/**
 * The outcome of analyzing a thought.
 */
public enum AnalysisStatus {

    /** The thought was analyzed and is now waiting for the user to clarify it. */
    QUESTIONS_REQUIRED,

    /** The thought was analyzed and nothing about it needs clarifying. */
    NO_QUESTIONS
}
