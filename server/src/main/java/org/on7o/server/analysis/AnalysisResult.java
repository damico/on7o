package org.on7o.server.analysis;

import org.on7o.server.clarification.ClarificationQuestion;

import java.util.List;

/**
 * What analyzing a thought produced.
 *
 * @param thoughtId         the analyzed thought
 * @param status            whether clarification is needed
 * @param reused            true when this was read back from stored artifacts, with no model call
 * @param semanticArtifact  filename of the extracted semantics (rThought)
 * @param questionsArtifact filename of the questions ontology (qThought)
 * @param questions         the questions now being asked
 */
public record AnalysisResult(
        String thoughtId,
        AnalysisStatus status,
        boolean reused,
        String semanticArtifact,
        String questionsArtifact,
        List<ClarificationQuestion> questions) {
}
