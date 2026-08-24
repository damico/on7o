package org.on7o.server.analysis;

import org.on7o.server.ontology.KnowledgeMetrics;

import java.util.List;

/**
 * What consolidating a thought produced.
 *
 * @param thoughtId          the consolidated thought
 * @param status             whether the artifact was produced, and why not when it was not
 * @param reused             true when this was read back from a stored artifact, with no model call
 * @param artifact           filename of the consolidated ontology, or null when nothing was produced
 * @param metrics            size of the produced artifact
 * @param openRequiredIds    ids of required questions still unanswered, empty when none are
 */
public record ConsolidationResult(
        String thoughtId,
        ConsolidationStatus status,
        boolean reused,
        String artifact,
        KnowledgeMetrics metrics,
        List<String> openRequiredIds) {

    /** Nothing was consolidated, because the user still owes the system answers. */
    public static ConsolidationResult blocked(String thoughtId, List<String> openRequiredIds) {
        return new ConsolidationResult(thoughtId, ConsolidationStatus.MISSING_REQUIRED_ANSWERS,
                false, null, KnowledgeMetrics.empty(), openRequiredIds);
    }
}
