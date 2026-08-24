package org.on7o.server.web;

import org.on7o.server.clarification.ClarificationQuestion;
import org.on7o.server.clarification.ClarificationService;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.stt.Transcription;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serves the clarification questions page for a given thought.
 *
 * <p>This page is the UI for stage 2 (qThought): the user reads the generated
 * questions and provides answers that will feed into stage 3 (cThought).
 */
@Controller
public class QuestionsController {

    private final ThoughtStore store;
    private final ClarificationService clarification;

    public QuestionsController(ThoughtStore store, ClarificationService clarification) {
        this.store = store;
        this.clarification = clarification;
    }

    /**
     * Renders the questions page for a thought.
     *
     * @param id    the thought id
     * @param model Thymeleaf model
     * @return template name, or redirect to index if prerequisites are missing
     */
    @GetMapping("/thoughts/{id}/questions")
    public String questions(@PathVariable String id, Model model) throws IOException {
        Optional<Thought> thoughtOpt = store.find(id);
        if (thoughtOpt.isEmpty()) {
            return "redirect:/";
        }
        Thought thought = thoughtOpt.get();

        List<ClarificationQuestion> questions = clarification.activeQuestions(id);
        if (questions.isEmpty()) {
            return "redirect:/";
        }

        Optional<Transcription> transcriptionOpt = store.findTranscription(id);
        Optional<String> cThoughtOpt = store.findConsolidatedThought(id);

        model.addAttribute("thoughtId", id);
        model.addAttribute("questions", questions);
        model.addAttribute("questionIds", questions.stream().map(ClarificationQuestion::id).toList());
        model.addAttribute("answers", currentAnswerTexts(id));
        model.addAttribute("transcriptionText",
                transcriptionOpt.map(Transcription::text).orElse(""));
        model.addAttribute("hasCThought", cThoughtOpt.isPresent());
        model.addAttribute("cThought", cThoughtOpt.orElse(""));
        model.addAttribute("derived", thought.isDerived());
        model.addAttribute("sourceEntity", thought.sourceEntity());
        model.addAttribute("parentId", thought.parentId());

        return "questions";
    }

    /**
     * The current answer to each question, so that reopening the page shows what
     * the user already wrote instead of an empty form.
     *
     * <p>A skipped question maps to nothing: the user declined it, and showing
     * them a blank box is exactly right.
     */
    private Map<String, String> currentAnswerTexts(String thoughtId) {
        Map<String, String> texts = new LinkedHashMap<>();
        clarification.currentAnswers(thoughtId).forEach((questionId, revision) -> {
            if (revision.answer() != null) {
                texts.put(questionId, revision.answer());
            }
        });
        return texts;
    }
}
