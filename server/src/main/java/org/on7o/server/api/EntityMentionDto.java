package org.on7o.server.api;

import java.time.Instant;

/**
 * One consolidated thought that mentioned an entity, as something to pick from a
 * list.
 *
 * <p>The id alone is not a choice a person can make. What tells two thoughts
 * apart is when they were captured and what was said in them, so both travel
 * with the id and the caller does not have to fetch each thought to label it.
 *
 * @param thoughtId  id of the thought
 * @param capturedAt when it was captured
 * @param summary    the opening of what was said, or the entity it was derived from
 */
public record EntityMentionDto(String thoughtId, Instant capturedAt, String summary) {
}
