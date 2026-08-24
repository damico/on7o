package org.on7o.server.api;

import org.on7o.server.analysis.ConsolidationResult;
import org.on7o.server.analysis.ConsolidationStatus;

import java.util.List;

/**
 * What consolidating a thought produced.
 *
 * <p>The counts describe the artifact, not the global knowledge base: nothing is
 * merged into the shared dataset until reconciliation.
 *
 * @param thoughtId       the consolidated thought
 * @param status          whether an artifact was produced
 * @param reused          true when a stored artifact was returned without calling the model
 * @param artifact        filename of the consolidated ontology, null when nothing was produced
 * @param statements      triples in the artifact
 * @param entities        distinct individuals in the artifact
 * @param relationships   assertions linking one resource to another
 * @param openRequiredIds ids of required questions still unanswered
 */
public record ConsolidationResponse(
        String thoughtId,
        ConsolidationStatus status,
        boolean reused,
        String artifact,
        int statements,
        int entities,
        int relationships,
        List<String> openRequiredIds) {

    /** Presents a consolidation result. */
    public static ConsolidationResponse of(ConsolidationResult result) {
        return new ConsolidationResponse(
                result.thoughtId(),
                result.status(),
                result.reused(),
                result.artifact(),
                result.metrics().statements(),
                result.metrics().entities(),
                result.metrics().relationships(),
                result.openRequiredIds());
    }
}
