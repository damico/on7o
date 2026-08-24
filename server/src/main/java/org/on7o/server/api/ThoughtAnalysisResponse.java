package org.on7o.server.api;

import org.on7o.server.analysis.AnalysisResult;
import org.on7o.server.analysis.AnalysisStatus;

import java.util.List;

/**
 * What analyzing a thought produced.
 *
 * @param thoughtId         the analyzed thought
 * @param analysisStatus    whether the thought now needs clarifying
 * @param reused            true when stored artifacts were returned without calling the model
 * @param semanticArtifact  filename of the extracted semantics
 * @param questionsArtifact filename of the questions ontology
 * @param questions         the questions now being asked
 */
public record ThoughtAnalysisResponse(
        String thoughtId,
        AnalysisStatus analysisStatus,
        boolean reused,
        String semanticArtifact,
        String questionsArtifact,
        List<ClarificationQuestionDto> questions) {

    /** Presents an analysis result. */
    public static ThoughtAnalysisResponse of(AnalysisResult result) {
        return new ThoughtAnalysisResponse(
                result.thoughtId(),
                result.status(),
                result.reused(),
                result.semanticArtifact(),
                result.questionsArtifact(),
                result.questions().stream().map(ClarificationQuestionDto::of).toList());
    }
}
