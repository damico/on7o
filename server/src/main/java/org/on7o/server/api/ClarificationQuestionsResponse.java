package org.on7o.server.api;

import org.on7o.server.clarification.ClarificationQuestion;

import java.util.List;

/**
 * The questions a thought is asking.
 *
 * @param thoughtId the thought
 * @param questions its questions, obsolete ones included so that history stays queryable
 */
public record ClarificationQuestionsResponse(
        String thoughtId,
        List<ClarificationQuestionDto> questions) {

    /** Presents a list of stored questions. */
    public static ClarificationQuestionsResponse of(String thoughtId,
                                                    List<ClarificationQuestion> questions) {
        return new ClarificationQuestionsResponse(
                thoughtId, questions.stream().map(ClarificationQuestionDto::of).toList());
    }
}
