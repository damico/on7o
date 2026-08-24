package org.on7o.server.api;

import java.util.List;

/**
 * What a thought has been answered.
 *
 * @param thoughtId the thought
 * @param answers   the current answer to each question that has one
 * @param history   every revision ever submitted, oldest first, when asked for
 */
public record ClarificationAnswersResponse(
        String thoughtId,
        List<ClarificationAnswerView> answers,
        List<ClarificationAnswerView> history) {
}
