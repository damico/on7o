package org.on7o.server.api;

import org.on7o.server.ingest.EntityThoughtService;
import org.on7o.server.ingest.Thought;
import org.on7o.server.llm.InterpretationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * REST endpoint for manually turning one entity node, picked by the user from
 * a diagram, into its own derived thought.
 */
@RestController
public class EntityController {

    private final EntityThoughtService entityThoughtService;

    public EntityController(EntityThoughtService entityThoughtService) {
        this.entityThoughtService = entityThoughtService;
    }

    /**
     * Creates a new thought derived from one entity and generates its
     * clarification questions right away.
     *
     * @param parentId id of the thought whose diagram the entity came from
     * @param request  the entity's source stage and display label
     * @return the new thought's id and the URL of its questions page
     */
    @PostMapping("/api/thoughts/{parentId}/entities")
    public EntityDeriveResponse derive(@PathVariable String parentId,
                                       @RequestBody EntityDeriveRequest request) {
        try {
            Thought derived = entityThoughtService.deriveEntity(parentId, request.stage(), request.label());
            return new EntityDeriveResponse(derived.id(), "/thoughts/" + derived.id() + "/questions");
        } catch (InterpretationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "failed to save derived thought");
        }
    }
}
