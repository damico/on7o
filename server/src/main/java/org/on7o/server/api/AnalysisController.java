package org.on7o.server.api;

import jakarta.validation.Valid;
import org.on7o.server.analysis.AnalysisResult;
import org.on7o.server.analysis.ConsolidationResult;
import org.on7o.server.analysis.ConsolidationService;
import org.on7o.server.analysis.ThoughtAnalysisService;
import org.on7o.server.reconcile.ReconciliationResult;
import org.on7o.server.reconcile.ReconciliationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Synchronous interpretation of a thought.
 *
 * <pre>
 * POST /api/thoughts/{id}/analyze
 * POST /api/thoughts/{id}/consolidate
 * </pre>
 *
 * <p>The web UI drives the same two stages over Server-Sent Events, because a
 * model call takes long enough that a browser needs to be told what is
 * happening. These endpoints exist for everything that is not a browser: they
 * answer once, with the whole result, which is what makes the pipeline testable
 * without a client that speaks SSE.
 *
 * <p>Both are safe to repeat. Analysis and consolidation read back their stored
 * artifacts instead of paying for the model again, unless the caller asks for a
 * re-run with {@code force}.
 */
@RestController
public class AnalysisController {

    private final ThoughtAnalysisService analysis;
    private final ConsolidationService consolidation;
    private final ReconciliationService reconciliation;

    public AnalysisController(ThoughtAnalysisService analysis,
                              ConsolidationService consolidation,
                              ReconciliationService reconciliation) {
        this.analysis = analysis;
        this.consolidation = consolidation;
        this.reconciliation = reconciliation;
    }

    /**
     * Extracts what the thought states and asks what it leaves unresolved.
     *
     * @param id      the thought to analyze
     * @param request options, or null to accept the defaults
     */
    @PostMapping(value = "/api/thoughts/{id}/analyze", produces = MediaType.APPLICATION_JSON_VALUE)
    public ThoughtAnalysisResponse analyze(
            @PathVariable String id,
            @Valid @RequestBody(required = false) ThoughtAnalysisRequest request) throws IOException {

        ThoughtAnalysisRequest options = request != null ? request : ThoughtAnalysisRequest.defaults();
        AnalysisResult result = analysis.analyze(id, options.forced());
        return ThoughtAnalysisResponse.of(result);
    }

    /**
     * Turns the thought, its questions and its answers into one knowledge
     * artifact.
     *
     * <p>Answering is the user's job, so an unanswered required question stops
     * this and says which one, rather than letting the model invent the missing
     * half.
     *
     * @param id      the thought to consolidate
     * @param request options, or null to accept the defaults
     */
    @PostMapping(value = "/api/thoughts/{id}/consolidate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ConsolidationResponse consolidate(
            @PathVariable String id,
            @Valid @RequestBody(required = false) ConsolidationRequest request) throws IOException {

        ConsolidationRequest options = request != null ? request : ConsolidationRequest.defaults();
        ConsolidationResult result =
                consolidation.consolidate(id, options.forced(), options.incompleteAllowed());
        return ConsolidationResponse.of(result);
    }

    /**
     * Merges the thought's consolidated knowledge into the HCIN.
     *
     * <p>Safe to repeat: entity URIs are a function of what the entities are,
     * so a second merge of the same thought adds nothing and reports zero.
     *
     * <p>Nothing is promoted by merging. A statement the thought only suspected
     * is still only suspected once it is in the network, and authority stated
     * without a scope is recorded as a hypothesis however confidently it was
     * said, because one approval is not standing power.
     *
     * @param id the thought to merge
     */
    @PostMapping(value = "/api/thoughts/{id}/reconcile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReconciliationResponse reconcile(@PathVariable String id) {
        return ReconciliationResponse.of(reconciliation.reconcile(id));
    }
}
