package org.on7o.server.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.on7o.server.clarification.AnswerSubmission;

/**
 * One answer being submitted.
 *
 * <p>Either say something or say explicitly that you are skipping: an empty
 * answer that is not marked as skipped is almost always a client bug, and
 * accepting it would record silence as if it were knowledge.
 *
 * @param questionId the question being answered
 * @param answer     the answer in plain language, omitted when skipping
 * @param skipped    true to decline the question on purpose
 */
public record ClarificationAnswerDto(

        @NotBlank(message = "questionId is required")
        String questionId,

        @Size(max = MAX_ANSWER_LENGTH, message = "answer must be at most " + MAX_ANSWER_LENGTH + " characters")
        String answer,

        Boolean skipped) {

    /** Upper bound on a single answer. */
    public static final int MAX_ANSWER_LENGTH = 10_000;

    /** True when the user declined the question. */
    public boolean isSkipped() {
        return Boolean.TRUE.equals(skipped);
    }

    /** True when this carries neither an answer nor an explicit skip. */
    public boolean isEmpty() {
        return !isSkipped() && (answer == null || answer.isBlank());
    }

    /** As the clarification domain takes it. */
    public AnswerSubmission toSubmission() {
        return new AnswerSubmission(questionId, answer, isSkipped());
    }
}
