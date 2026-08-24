package org.on7o.server.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Answers to some or all of a thought's questions.
 *
 * <p>Answers may arrive a few at a time and in any order: each one names the
 * question it belongs to, and questions left out of the request are left alone.
 *
 * @param answers the answers being submitted
 */
public record ClarificationAnswersRequest(

        @NotEmpty(message = "answers is required and must not be empty")
        @Valid
        List<ClarificationAnswerDto> answers) {
}
