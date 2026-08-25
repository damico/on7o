package org.on7o.server.api;

import org.on7o.server.projection.RelationshipVectorDelta;

import java.time.Instant;
import java.util.List;

/**
 * What moved in the network between two instants.
 *
 * @param from    the earlier instant
 * @param to      the later instant
 * @param ego     whose point of view this is from
 * @param changes one entry per entity known at either instant, largest change first
 */
public record ProjectionDeltaResponse(
        Instant from,
        Instant to,
        String ego,
        List<RelationshipVectorDelta> changes) {
}
