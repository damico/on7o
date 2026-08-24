package org.on7o.server.projection;

import java.math.BigDecimal;
import java.util.List;

/**
 * An organization, drawn as something enclosing its people rather than as a node
 * among them.
 *
 * <p>A person may appear in more than one group, and a person in no group at all
 * is not an error: someone can matter to the ego without belonging anywhere.
 *
 * <p>A group carries the financial standing of everyone inside it, summarized as
 * the direction of its balance. This reads differently from an edge on purpose:
 * an edge says what happened, so money moving both ways makes it
 * {@link FlowSummary#BOTH}, while a group says where the ego ends up, so a
 * million in and two million out is an outflow. When the ego's own organization
 * is drawn, the ego's whole position is what colours it.
 *
 * @param id            the organization's HCIN URI
 * @param label         its name
 * @param memberNodeIds the people inside it, as node ids
 * @param flowDirection which way the balance with everyone inside it leans
 * @param strokeStyle   solid when money passes at all, dashed when none does
 * @param netBalance    what came in from this group minus what went out to it
 * @param currency      the currency that balance is in
 */
public record ProjectionGroup(
        String id,
        String label,
        List<String> memberNodeIds,
        FlowSummary flowDirection,
        FlowStyle strokeStyle,
        BigDecimal netBalance,
        String currency) {
}
