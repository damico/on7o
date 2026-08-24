package org.on7o.server.api;

import java.util.List;

/**
 * Everything the HCIN can hand out.
 *
 * @param artifacts the schema files and the dataset exports
 */
public record KnowledgeArtifactsResponse(List<KnowledgeArtifactDto> artifacts) {
}
