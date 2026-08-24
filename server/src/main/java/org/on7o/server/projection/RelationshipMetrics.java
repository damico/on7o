package org.on7o.server.projection;

/**
 * Everything the projection computed about one entity, at one instant.
 *
 * <p>Held together rather than returned as loose numbers because they are only
 * comparable within a single calculation: distances are normalized across the
 * population, so a distance from one projection means nothing next to a distance
 * from another.
 *
 * @param entityUri      who or what this is about
 * @param proximity      the score and the interactions that produced it
 * @param visualDistance how far from the centre, inside the configured band
 * @param magnitude      the money
 * @param authority      the decision power
 * @param radius         how big the node is drawn
 * @param vector         the same facts as the relationship vector
 */
public record RelationshipMetrics(
        String entityUri,
        ProximityScore proximity,
        double visualDistance,
        FinancialMagnitude magnitude,
        FinancialAuthorityState authority,
        double radius,
        RelationshipVector vector) {
}
