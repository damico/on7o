package org.on7o.server.projection;

import java.math.BigDecimal;

/**
 * The relationship between the ego and one other entity, as numbers, at one
 * instant.
 *
 * <p>The point of a vector rather than a single strength is that one number
 * cannot say what kind of relationship this is. Someone can be financially
 * central and interactionally distant, or the reverse, and collapsing that into
 * "0.87" throws away the only interesting part.
 *
 * <p>Two components are declared and not yet computed. They are null rather than
 * zero: the network does not know whether the ego depends on this person, and
 * saying "no dependency" would be an answer it has not earned.
 *
 * @param interactionProximity how close, from the interaction history
 * @param financialMagnitude   how much money moves, gross
 * @param authority            1 when the person holds relevant decision power, 0 when not
 * @param dependency           not computed yet, always null
 * @param reciprocity          not computed yet, always null
 */
public record RelationshipVector(
        Double interactionProximity,
        BigDecimal financialMagnitude,
        Double authority,
        Double dependency,
        Double reciprocity) {

    /** The vector for a person the network knows nothing quantitative about. */
    public static RelationshipVector empty() {
        return new RelationshipVector(0.0, BigDecimal.ZERO, 0.0, null, null);
    }
}
