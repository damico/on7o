package org.on7o.server.web;

import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.stt.Transcription;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.util.List;
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

    public QuestionsController(ThoughtStore store) {
        this.store = store;
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

        Optional<List<String>> questionsOpt = store.findQuestions(id);
        if (questionsOpt.isEmpty()) {
            return "redirect:/";
        }

        Optional<Transcription> transcriptionOpt = store.findTranscription(id);
        Optional<String> cThoughtOpt = store.findConsolidatedThought(id);

        model.addAttribute("thoughtId", id);
        model.addAttribute("questions", questionsOpt.get());
        model.addAttribute("transcriptionText",
                transcriptionOpt.map(Transcription::text).orElse(""));
        model.addAttribute("hasCThought", cThoughtOpt.isPresent());
        model.addAttribute("cThought", cThoughtOpt.orElse(""));
        model.addAttribute("derived", thought.isDerived());
        model.addAttribute("sourceEntity", thought.sourceEntity());
        model.addAttribute("parentId", thought.parentId());

        return "questions";
    }
}
