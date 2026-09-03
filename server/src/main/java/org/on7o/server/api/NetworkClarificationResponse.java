package org.on7o.server.api;

import org.on7o.server.clarification.ClarificationQuestion;

import java.util.List;

/**
 * What asking the network about its own gaps produced.
 *
 * @param gapsFound    how many gaps the shapes reported across the knowledge graphs
 * @param asked        the questions this run added; empty when every gap was already asked about
 * @param questions    the added questions, with the node and property each is about
 * @param unattributed nodes carrying a gap that no thought is recorded as having
 *                     observed, which therefore cannot be asked about
 */
public record NetworkClarificationResponse(
        int gapsFound,
        int asked,
        List<ClarificationQuestionDto> questions,
        List<String> unattributed) {

    /** Reads the service result into the API contract. */
    public static NetworkClarificationResponse of(int gapsFound,
                                                  List<ClarificationQuestion> asked,
                                                  List<String> unattributed) {
        return new NetworkClarificationResponse(
                gapsFound,
                asked.size(),
                asked.stream().map(ClarificationQuestionDto::of).toList(),
                unattributed);
    }
}
