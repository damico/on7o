package org.on7o.server.projection;

import java.math.BigDecimal;
import java.util.List;

/**
 * One node of the financial projection.
 *
 * <p>Carries what to draw, not where: a radius and a distance from the centre,
 * never an x and a y. Where a node actually lands is layout, and layout is a
 * decision for whatever is drawing, not a fact about the network.
 *
 * @param id                   the entity's HCIN URI
 * @param label                the name the ego knows it by
 * @param type                 ego, person or organization
 * @param organizationIds      every organization it belongs to, possibly none
 * @param interactionProximity how close, from the interaction history
 * @param visualDistance       how far from the centre, inside the configured band
 * @param financialMagnitude   gross volume of money, not the balance
 * @param grossInflow          what came in from this side
 * @param grossOutflow         what went out this way
 * @param netBalance           inflow minus outflow, which may be negative
 * @param currency             the currency those figures are in
 * @param radius               how big to draw it
 * @param financialAuthority   what decision power this entity holds
 * @param interactions         how many interactions the proximity was computed from
 */
public record ProjectionNode(
        String id,
        String label,
        NodeType type,
        List<String> organizationIds,
        double interactionProximity,
        double visualDistance,
        BigDecimal financialMagnitude,
        BigDecimal grossInflow,
        BigDecimal grossOutflow,
        BigDecimal netBalance,
        String currency,
        double radius,
        FinancialAuthorityState financialAuthority,
        int interactions) {

    /** True when this entity belongs to nothing the network knows of. */
    public boolean isIndependent() {
        return organizationIds.isEmpty();
    }
}
