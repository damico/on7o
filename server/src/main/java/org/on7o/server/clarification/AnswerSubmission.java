package org.on7o.server.clarification;

/**
 * One answer as submitted by a user, before it becomes a stored revision.
 *
 * @param questionId the question being answered
 * @param answer     what the user wrote, ignored when skipped
 * @param skipped    true when the user declines to answer
 */
public record AnswerSubmission(String questionId, String answer, boolean skipped) {

    /** True when there is nothing to record: no text and no explicit skip. */
    public boolean isEmpty() {
        return !skipped && (answer == null || answer.isBlank());
    }

    /** The answer text as it should be stored, or null when skipped. */
    public String normalizedAnswer() {
        return skipped || answer == null ? null : answer.trim();
    }
}
