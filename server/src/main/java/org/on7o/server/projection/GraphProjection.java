package org.on7o.server.projection;

import java.time.Instant;
import java.util.List;

/**
 * The financial projection: the HCIN read from the ego's point of view, in the
 * financial layer, at one instant.
 *
 * <p>This is a computed view, not the network itself. The same HCIN produces a
 * different projection under a different perspective, context or time, without
 * anything underneath changing.
 *
 * <p>Renderer-independent by design. It says how far apart things are and how
 * big they should be; it does not say where they go.
 *
 * @param asOf   the instant this was computed for
 * @param ego    the URI everything is relative to
 * @param nodes  the entities worth drawing
 * @param edges  the ego's relationships to them
 * @param groups the organizations enclosing them
 */
public record GraphProjection(
        Instant asOf,
        String ego,
        List<ProjectionNode> nodes,
        List<ProjectionEdge> edges,
        List<ProjectionGroup> groups) {
}
