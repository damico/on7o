package org.on7o.server.api;

import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtStore;
import org.on7o.server.stt.Transcription;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where an entity in the network came from.
 *
 * <pre>
 * GET /api/hcin/mentions?entity=urn:hcin:person:ninoska
 * </pre>
 *
 * <p>A node on a projection is a name and a few numbers, and a reader who wants
 * to know why it is there has to go back to what was actually said. Provenance
 * already records which thought observed which node; this turns that into
 * something a person can read and open.
 *
 * <p>Only thoughts that were consolidated are listed. The list exists to be
 * clicked through to a cThought, and offering a thought that has none would be
 * offering a door with nothing behind it.
 */
@RestController
public class EntityMentionsController {

    /** How much of what was said is enough to tell two thoughts apart. */
    private static final int SUMMARY_LENGTH = 120;

    private final HcinRepository repository;
    private final ThoughtStore thoughts;

    public EntityMentionsController(HcinRepository repository, ThoughtStore thoughts) {
        this.repository = repository;
        this.thoughts = thoughts;
    }

    /**
     * The consolidated thoughts that mentioned one entity, earliest first.
     *
     * @param entity URI of the entity, as the projection knows it
     */
    @GetMapping(value = "/api/hcin/mentions", produces = MediaType.APPLICATION_JSON_VALUE)
    public EntityMentionsResponse mentions(@RequestParam String entity) {
        List<EntityMentionDto> mentions = new ArrayList<>();

        for (String thoughtId : repository.thoughtsMentioning(entity)) {
            if (thoughts.findConsolidatedThought(thoughtId).isEmpty()) {
                continue;
            }
            thoughts.find(thoughtId)
                    .map(thought -> new EntityMentionDto(
                            thoughtId, thought.capturedAt(), summaryOf(thought)))
                    .ifPresent(mentions::add);
        }

        return new EntityMentionsResponse(entity, mentions);
    }

    /**
     * What the thought was about, in one line.
     *
     * <p>A captured thought is summarized by the opening of its transcription,
     * which is what the user actually said. A derived thought has no
     * transcription and is named by the entity it was derived from.
     */
    private String summaryOf(Thought thought) {
        if (thought.isDerived()) {
            return thought.sourceEntity();
        }
        Optional<String> text = thoughts.findTranscription(thought.id()).map(Transcription::text);
        return text.map(EntityMentionsController::shorten).orElse("");
    }

    private static String shorten(String text) {
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= SUMMARY_LENGTH
                ? flat
                : flat.substring(0, SUMMARY_LENGTH).stripTrailing() + "...";
    }
}
