package org.on7o.server.projection;

import java.math.BigDecimal;

/**
 * How one relationship changed between two instants.
 *
 * <p>Implements the difference the model defines over the relationship vector:
 * each component at the later instant minus the same component at the earlier
 * one. It is what turns the projection from a picture into a question that can
 * be asked, since whether a relationship is warming or cooling is not visible in
 * either snapshot alone.
 *
 * <p>A component nobody computes stays null in both vectors and in the
 * difference. Reporting it as a change of zero would claim the system had looked
 * and found nothing moving, which is not the same as never having looked.
 *
 * @param entityUri            who or what the relationship is with
 * @param label                the name the ego knows them by
 * @param before               the vector at the earlier instant
 * @param after                the vector at the later instant
 * @param interactionProximity change in how close they are
 * @param financialMagnitude   change in gross money moved
 * @param authority            change in decision power held
 * @param dependency           not computed yet, always null
 * @param reciprocity          not computed yet, always null
 */
public record RelationshipVectorDelta(
        String entityUri,
        String label,
        RelationshipVector before,
        RelationshipVector after,
        Double interactionProximity,
        BigDecimal financialMagnitude,
        Double authority,
        Double dependency,
        Double reciprocity) {

    /**
     * The difference between two vectors for one entity.
     *
     * <p>An entity present at one instant and absent at the other is compared
     * against an empty vector: the ego had no relationship with them then, and
     * that is a real starting point rather than missing data.
     */
    public static RelationshipVectorDelta between(String entityUri,
                                                  String label,
                                                  RelationshipVector before,
                                                  RelationshipVector after) {
        return new RelationshipVectorDelta(
                entityUri,
                label,
                before,
                after,
                subtract(after.interactionProximity(), before.interactionProximity()),
                subtract(after.financialMagnitude(), before.financialMagnitude()),
                subtract(after.authority(), before.authority()),
                subtract(after.dependency(), before.dependency()),
                subtract(after.reciprocity(), before.reciprocity()));
    }

    /** True when nothing about this relationship moved. */
    public boolean isUnchanged() {
        return isZero(interactionProximity)
                && (financialMagnitude == null || financialMagnitude.signum() == 0)
                && isZero(authority);
    }

    private static Double subtract(Double after, Double before) {
        return after == null || before == null ? null : after - before;
    }

    private static BigDecimal subtract(BigDecimal after, BigDecimal before) {
        return after == null || before == null ? null : after.subtract(before);
    }

    private static boolean isZero(Double value) {
        return value == null || value == 0.0;
    }
}
