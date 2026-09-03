package org.on7o.server.clarification;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.List;

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
 * @param kind         how the question expects to be answered
 * @param options      the values it offers, empty when it offers none
 */
public record ClarificationQuestion(
        String id,
        String thoughtId,
        String text,
        String subjectRef,
        String predicateRef,
        boolean required,
        QuestionStatus status,
        Instant createdAt,
        AnswerKind kind,
        List<String> options) {

    /**
     * A question read back without a status was written before statuses existed,
     * and one read back without a kind before questions could offer anything.
     * Both are free-text questions, which is what they were when they were saved.
     */
    public ClarificationQuestion {
        if (status == null) {
            status = QuestionStatus.OPEN;
        }
        if (kind == null) {
            kind = AnswerKind.TEXT;
        }
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** A question with nothing to offer, which is every question the user is asked in prose. */
    public ClarificationQuestion(String id,
                                 String thoughtId,
                                 String text,
                                 String subjectRef,
                                 String predicateRef,
                                 boolean required,
                                 QuestionStatus status,
                                 Instant createdAt) {
        this(id, thoughtId, text, subjectRef, predicateRef, required, status, createdAt,
                AnswerKind.TEXT, List.of());
    }

    /** The same question in a new state. */
    public ClarificationQuestion withStatus(QuestionStatus newStatus) {
        return new ClarificationQuestion(
                id, thoughtId, text, subjectRef, predicateRef, required, newStatus, createdAt,
                kind, options);
    }

    /**
     * The same question, put again as the generator would put it now.
     *
     * <p>Only meaningful while the question is still open. The id says which gap
     * is being asked about; the wording and the options are how it is being put,
     * and both can improve without the question becoming a different question. A
     * context named in a thought recorded yesterday is a choice this question
     * could not have offered when it was first asked.
     */
    public ClarificationQuestion withOffer(String newText, AnswerKind newKind, List<String> newOptions) {
        return new ClarificationQuestion(
                id, thoughtId, newText, subjectRef, predicateRef, required, status, createdAt,
                newKind, newOptions);
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
