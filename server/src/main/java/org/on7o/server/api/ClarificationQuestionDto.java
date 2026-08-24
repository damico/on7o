package org.on7o.server.api;

import org.on7o.server.clarification.ClarificationQuestion;
import org.on7o.server.clarification.QuestionStatus;

import java.time.Instant;

/**
 * A clarification question as the API presents it.
 *
 * <p>{@code thoughtId} is the question's provenance: it says which thought
 * raised it, and it is what later lets an answer be traced back to the moment
 * the system did not know something.
 *
 * @param id           stable id, which answers reference
 * @param thoughtId    the thought this question came from
 * @param text         the question in plain language
 * @param subjectRef   URI of the entity in question, when known
 * @param predicateRef URI of the relation in question, when known
 * @param required     whether consolidation waits for it
 * @param status       OPEN, ANSWERED, SKIPPED or OBSOLETE
 * @param createdAt    when the question was generated
 */
public record ClarificationQuestionDto(
        String id,
        String thoughtId,
        String text,
        String subjectRef,
        String predicateRef,
        boolean required,
        QuestionStatus status,
        Instant createdAt) {

    /** Presents a stored question. */
    public static ClarificationQuestionDto of(ClarificationQuestion question) {
        return new ClarificationQuestionDto(
                question.id(),
                question.thoughtId(),
                question.text(),
                question.subjectRef(),
                question.predicateRef(),
                question.required(),
                question.status(),
                question.createdAt());
    }
}
