package org.on7o.server.api;

import java.util.List;

/**
 * Where an entity came from: every consolidated thought that mentioned it.
 *
 * @param entity   URI the mentions were asked about
 * @param mentions the thoughts, earliest first
 */
public record EntityMentionsResponse(String entity, List<EntityMentionDto> mentions) {
}
