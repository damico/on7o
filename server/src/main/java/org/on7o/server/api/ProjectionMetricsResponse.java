package org.on7o.server.api;

import org.on7o.server.projection.RelationshipMetrics;

import java.time.Instant;
import java.util.List;

/**
 * The measurements behind a projection, without the drawing decisions.
 *
 * <p>Separate from the projection itself so that a number on a screen can be
 * checked against what produced it: each entry carries the interactions its
 * proximity was computed from, with their weights and their decay.
 *
 * @param asOf    the instant these were computed for
 * @param ego     whose point of view they are from
 * @param metrics one entry per entity, closest first
 */
public record ProjectionMetricsResponse(Instant asOf, String ego, List<RelationshipMetrics> metrics) {
}
