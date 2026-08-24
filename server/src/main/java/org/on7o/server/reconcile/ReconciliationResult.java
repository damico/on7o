package org.on7o.server.reconcile;

/**
 * What merging one thought into the HCIN did.
 *
 * <p>The statement counts are per epistemic tier, because how much the network
 * grew matters less than what it now claims to know. A thought that adds twenty
 * hypotheses and nothing asserted has told the system a great deal about what to
 * ask next and almost nothing about the world.
 *
 * <p>Re-reconciling a thought that was already merged is a success with every
 * count at zero: the URIs are the same, so there is nothing left to add.
 *
 * @param thoughtId              the thought merged
 * @param status                 whether there was anything to merge
 * @param entitiesCreated        entities the HCIN had never heard of
 * @param entitiesMatched        entities it already knew
 * @param relationshipsCreated   qualified relationships added
 * @param statementsAsserted     triples added to what is held as fact
 * @param statementsInferred     triples added to what was derived
 * @param statementsHypothesized triples added to what is merely suspected
 */
public record ReconciliationResult(
        String thoughtId,
        ReconciliationStatus status,
        int entitiesCreated,
        int entitiesMatched,
        int relationshipsCreated,
        long statementsAsserted,
        long statementsInferred,
        long statementsHypothesized) {

    /** Nothing in the thought could be merged. */
    public static ReconciliationResult nothing(String thoughtId) {
        return new ReconciliationResult(thoughtId, ReconciliationStatus.NOTHING_TO_MERGE,
                0, 0, 0, 0, 0, 0);
    }
}
