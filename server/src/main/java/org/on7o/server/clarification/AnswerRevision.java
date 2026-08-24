package org.on7o.server.clarification;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * One version of the user's answer to one question.
 *
 * <p>Answers are appended, never overwritten: a user who corrects an answer
 * leaves both versions on record, so what the system believed at any point can
 * still be explained. The current answer to a question is simply its highest
 * revision.
 *
 * @param questionId the question this answers
 * @param answer     what the user wrote, or null when skipped
 * @param skipped    true when the user was asked and declined to answer
 * @param answeredAt when this revision was submitted
 * @param revision   1 for the first answer to a question, incremented on each change
 */
public record AnswerRevision(
        String questionId,
        String answer,
        boolean skipped,
        Instant answeredAt,
        int revision) {

    /** The status this revision puts its question in. */
    @JsonIgnore
    public QuestionStatus resultingStatus() {
        return skipped ? QuestionStatus.SKIPPED : QuestionStatus.ANSWERED;
    }
}
