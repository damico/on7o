package org.on7o.server.api;

import jakarta.validation.Valid;
import org.on7o.server.clarification.AnswerSubmission;
import org.on7o.server.clarification.ClarificationQuestion;
import org.on7o.server.clarification.ClarificationService;
import org.on7o.server.clarification.NetworkAnswerService;
import org.on7o.server.ingest.ThoughtNotFoundException;
import org.on7o.server.ingest.ThoughtStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * Clarification as a resource: what a thought is asking, and what it has been
 * told.
 *
 * <pre>
 * GET  /api/thoughts/{id}/questions
 * POST /api/thoughts/{id}/answers
 * GET  /api/thoughts/{id}/answers
 * </pre>
 *
 * <p>Every question has an id and every answer names one, so answers may arrive
 * a few at a time, in any order, and a correction is a new revision rather than
 * an overwrite. What the user said last is the answer; what they said before is
 * still on record.
 */
@RestController
public class ClarificationController {

    private final ClarificationService clarification;
    private final NetworkAnswerService networkAnswers;
    private final ThoughtStore thoughts;

    public ClarificationController(ClarificationService clarification,
                                   NetworkAnswerService networkAnswers,
                                   ThoughtStore thoughts) {
        this.clarification = clarification;
        this.networkAnswers = networkAnswers;
        this.thoughts = thoughts;
    }

    /**
     * The thought's questions, obsolete ones included: a question that was
     * really asked stays queryable, including after consolidation.
     */
    @GetMapping(value = "/api/thoughts/{id}/questions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ClarificationQuestionsResponse questions(@PathVariable String id) {
        requireThought(id);
        return ClarificationQuestionsResponse.of(id, clarification.allQuestions(id));
    }

    /**
     * Records answers and returns the questions as they now stand.
     *
     * @param id      the thought being clarified
     * @param request the answers, for any subset of the questions
     */
    @PostMapping(value = "/api/thoughts/{id}/answers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ClarificationQuestionsResponse submitAnswers(
            @PathVariable String id,
            @Valid @RequestBody ClarificationAnswersRequest request) throws IOException {

        requireThought(id);

        List<AnswerSubmission> submissions = request.answers().stream()
                .filter(answer -> !answer.isEmpty())
                .map(ClarificationAnswerDto::toSubmission)
                .toList();

        List<ClarificationQuestion> questions = clarification.submitAnswers(id, submissions);

        // An answer to a question the network raised about itself is knowledge,
        // not just a record of what was said. Writing it back is what closes the
        // gap the question came from.
        networkAnswers.apply(id);

        return ClarificationQuestionsResponse.of(id, questions);
    }

    /**
     * What the thought has been answered.
     *
     * @param id      the thought
     * @param history true to also return every superseded revision
     */
    @GetMapping(value = "/api/thoughts/{id}/answers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ClarificationAnswersResponse answers(@PathVariable String id,
                                                @RequestParam(defaultValue = "false") boolean history) {
        requireThought(id);

        List<ClarificationAnswerView> current = clarification.currentAnswers(id).values().stream()
                .map(ClarificationAnswerView::of)
                .toList();

        List<ClarificationAnswerView> revisions = history
                ? clarification.answerHistory(id).stream().map(ClarificationAnswerView::of).toList()
                : List.of();

        return new ClarificationAnswersResponse(id, current, revisions);
    }

    private void requireThought(String id) {
        if (thoughts.find(id).isEmpty()) {
            throw new ThoughtNotFoundException(id);
        }
    }
}
