package org.on7o.server.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Serves the read-only ontology diagram viewer page.
 *
 * <p>Intentionally kept separate from the REST controller under {@code api/}
 * that serves the same stage as JSON: this layer returns rendered HTML.
 */
@Controller
public class DiagramViewController {

    private static final Map<String, String> STAGE_TITLES = Map.of(
            "rthought", "rThought",
            "qthought", "qThought",
            "cthought", "cThought"
    );

    @GetMapping("/thoughts/{id}/diagram/{stage}")
    public String diagram(@PathVariable String id, @PathVariable String stage, Model model) {
        model.addAttribute("id", id);
        model.addAttribute("stage", stage);
        model.addAttribute("stageTitle", STAGE_TITLES.getOrDefault(stage, stage));
        return "ontology-diagram";
    }
}
