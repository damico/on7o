package org.on7o.server.projection;

import java.math.BigDecimal;

/**
 * One edge of the financial projection, always between the ego and someone else.
 *
 * <p>Direct flow and decision power are kept apart on purpose. A person can
 * approve payments they never receive, which is an edge with no flow at all
 * attached to a node that carries authority.
 *
 * @param source              the ego
 * @param target              the other end
 * @param directFinancialFlow whether money passes directly between them
 * @param flowDirection       which way, over the whole history up to this instant
 * @param strokeStyle         solid when money passes directly, dashed when it does not
 * @param amount              gross volume across this edge
 * @param currency            the currency that volume is in
 */
public record ProjectionEdge(
        String source,
        String target,
        boolean directFinancialFlow,
        FlowSummary flowDirection,
        FlowStyle strokeStyle,
        BigDecimal amount,
        String currency) {
}
