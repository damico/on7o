package org.on7o.server.clarification;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * One question the system needs answered before it can consolidate a thought.
 *
 * <p>The id is what makes clarification a resource rather than a position in a
 * list: answers reference it, so neither the order questions were asked in nor
 * the order answers arrive in carries any meaning.
 *
 * @param id           stable id, unique within the thought
 * @param thoughtId    the thought this question came from, its provenance
 * @param text         the plain-language question shown to the user
 * @param subjectRef   URI of the entity the question is about, or null when unknown
 * @param predicateRef URI of the relation the question is about, or null when unknown
 * @param required     whether consolidation should wait for it
 * @param status       where the question stands
 * @param createdAt    when the question was generated
 */
public record ClarificationQuestion(
        String id,
        String thoughtId,
        String text,
        String subjectRef,
        String predicateRef,
        boolean required,
        QuestionStatus status,
        Instant createdAt) {

    /** A question read back without a status was written before statuses existed. */
    public ClarificationQuestion {
        if (status == null) {
            status = QuestionStatus.OPEN;
        }
    }

    /** The same question in a new state. */
    public ClarificationQuestion withStatus(QuestionStatus newStatus) {
        return new ClarificationQuestion(
                id, thoughtId, text, subjectRef, predicateRef, required, newStatus, createdAt);
    }

    /** True when this question is no longer being asked. */
    @JsonIgnore
    public boolean isObsolete() {
        return status == QuestionStatus.OBSOLETE;
    }

    /** True when the question is still waiting for the user. */
    @JsonIgnore
    public boolean isOpen() {
        return status == QuestionStatus.OPEN;
    }
}
