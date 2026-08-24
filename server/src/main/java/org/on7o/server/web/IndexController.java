package org.on7o.server.web;

import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;

/**
 * Serves the main web UI for browsing captured thoughts.
 *
 * <p>Intentionally kept separate from the REST controllers under {@code api/}:
 * this layer returns rendered HTML, not JSON.
 */
@Controller
public class IndexController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final ThoughtStore store;

    public IndexController(ThoughtStore store) {
        this.store = store;
    }

    /**
     * Renders the thought list index page.
     *
     * @param limit optional cap on how many thoughts to display
     * @param model Thymeleaf model
     * @return template name
     */
    @GetMapping("/")
    public String index(@RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
                        Model model) throws IOException {

        int capped = Math.clamp(limit, 1, MAX_LIMIT);

        List<Thought> thoughts = store.list(capped);

        List<ThoughtView> views = thoughts.stream()
                .map(t -> new ThoughtView(
                        t,
                        store.findTranscription(t.id()),
                        store.findRawThought(t.id()),
                        store.hasFile(t.id(), ThoughtStore.QUESTIONS_FILE),
                        store.hasFile(t.id(), ThoughtStore.CTHOUGHT_FILE)
                ))
                .toList();

        model.addAttribute("thoughts", views);
        model.addAttribute("total", views.size());

        return "index";
    }
}
