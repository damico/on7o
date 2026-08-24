package org.on7o.server.api;

import org.on7o.server.reconcile.ReconciliationResult;
import org.on7o.server.reconcile.ReconciliationStatus;

/**
 * What merging a thought into the HCIN did.
 *
 * <p>Statements are counted per epistemic tier on purpose: how much the network
 * grew matters less than what it now claims to know.
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
public record ReconciliationResponse(
        String thoughtId,
        ReconciliationStatus status,
        int entitiesCreated,
        int entitiesMatched,
        int relationshipsCreated,
        long statementsAsserted,
        long statementsInferred,
        long statementsHypothesized) {

    /** Presents a reconciliation result. */
    public static ReconciliationResponse of(ReconciliationResult result) {
        return new ReconciliationResponse(
                result.thoughtId(),
                result.status(),
                result.entitiesCreated(),
                result.entitiesMatched(),
                result.relationshipsCreated(),
                result.statementsAsserted(),
                result.statementsInferred(),
                result.statementsHypothesized());
    }
}
