package org.on7o.server.clarification;

/**
 * Thrown when an answer references a question that the thought never asked.
 *
 * <p>Silently dropping such an answer would let a client believe it had answered
 * something it had not, so this is a client error, not a warning.
 */
public class UnknownQuestionException extends IllegalArgumentException {

    public UnknownQuestionException(String thoughtId, String questionId) {
        super("unknown question " + questionId + " for thought " + thoughtId);
    }
}
