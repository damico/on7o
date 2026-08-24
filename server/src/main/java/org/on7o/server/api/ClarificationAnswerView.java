package org.on7o.server.api;

import org.on7o.server.clarification.AnswerRevision;

import java.time.Instant;

/**
 * One recorded answer, as the API presents it.
 *
 * @param questionId the question this answers
 * @param answer     what the user wrote, null when skipped
 * @param skipped    true when the user declined the question
 * @param answeredAt when this version was submitted
 * @param revision   1 for the first answer to a question, higher for each correction
 */
public record ClarificationAnswerView(
        String questionId,
        String answer,
        boolean skipped,
        Instant answeredAt,
        int revision) {

    /** Presents a stored revision. */
    public static ClarificationAnswerView of(AnswerRevision revision) {
        return new ClarificationAnswerView(
                revision.questionId(),
                revision.answer(),
                revision.skipped(),
                revision.answeredAt(),
                revision.revision());
    }
}
