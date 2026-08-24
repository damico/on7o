package org.on7o.server.projection;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * How big a node is drawn, given the money behind it.
 *
 * <p>Financial data is skewed enough that a linear mapping would let one large
 * relationship swallow the picture, so the mapping is logarithmic and bounded at
 * both ends. A relationship a thousand times larger than another is drawn
 * noticeably bigger, not a thousand times bigger.
 */
@Service
public class NodeRadiusScale {

    private final ProjectionProperties properties;

    public NodeRadiusScale(ProjectionProperties properties) {
        this.properties = properties;
    }

    /**
     * The radius for a gross magnitude.
     *
     * @param gross the money behind the node, never negative
     * @return a radius inside the configured band
     */
    public double radiusFor(BigDecimal gross) {
        ProjectionProperties.Financial financial = properties.getFinancial();
        if (gross == null || gross.signum() <= 0) {
            return financial.getMinRadius();
        }

        double radius = financial.getMinRadius()
                + financial.getAlpha() * Math.log1p(gross.doubleValue());

        return Math.clamp(radius, financial.getMinRadius(), financial.getMaxRadius());
    }
}
