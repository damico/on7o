package org.on7o.server.reconcile;

import org.on7o.server.hcin.KnowledgeTier;

import java.math.BigDecimal;

/**
 * One claim a consolidated thought makes about two of its entities.
 *
 * @param sourceLocalUri the entity the claim is about
 * @param predicateUri   what is being claimed
 * @param targetLocalUri the entity it is claimed about
 * @param tier           how the thought said it knew this
 * @param confidence     how strongly, or null when the thought did not say
 */
public record CandidateStatement(
        String sourceLocalUri,
        String predicateUri,
        String targetLocalUri,
        KnowledgeTier tier,
        BigDecimal confidence) {
}
