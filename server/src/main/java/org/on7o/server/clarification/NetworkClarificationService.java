package org.on7o.server.clarification;

import org.on7o.server.hcin.HcinGraphs;
import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.hcin.ShaclFinding;
import org.on7o.server.hcin.ShaclValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lets the network ask about its own gaps.
 *
 * <p>Every other question in on7o comes from a thought: the user said something,
 * and the qThought stage asked what it could not resolve from the text. These
 * come from the other direction. The shapes know what an HCIN statement is meant
 * to say, so validating the graph produces a list of things the network is
 * missing, and each one is a question nobody has thought to ask.
 *
 * <p>Two decisions shape this class:
 *
 * <p><b>A gap is attached to the thought that produced it.</b> A node in the
 * graph has no conversation of its own, but the thought that introduced it does,
 * so the question joins that thought's questions and travels through the
 * lifecycle that already exists: the same page, the same answers, the same
 * revisions. A node no thought is recorded as having observed cannot be asked
 * about and is reported rather than guessed at.
 *
 * <p><b>Every gap the shapes report is asked about.</b> There used to be a
 * filter here, because the shapes asked which layer joined a venue to the city
 * it stands in and nobody could answer that. The shapes now ask only of edges
 * between people and organizations, so the filter would have nothing left to
 * remove. Where a question has no answer is a fact about the model, and it
 * belongs in the shapes, not in a second opinion held here.
 *
 * <p><b>These questions are never required.</b> A required question blocks
 * consolidation, and consolidation of the thought that produced the node has
 * already happened. Blocking it retroactively would strand the thought, and the
 * gap is real whether or not the user feels like closing it today.
 */
@Service
public class NetworkClarificationService {

    private static final Logger log = LoggerFactory.getLogger(NetworkClarificationService.class);

    /** The graphs holding claims about the world, rather than schema or provenance. */
    private static final List<String> KNOWLEDGE_GRAPHS =
            List.of(HcinGraphs.ASSERTED, HcinGraphs.INFERRED, HcinGraphs.HYPOTHESES);

    private final ShaclValidationService validation;
    private final HcinRepository repository;
    private final ClarificationService clarification;

    public NetworkClarificationService(ShaclValidationService validation,
                                       HcinRepository repository,
                                       ClarificationService clarification) {
        this.validation = validation;
        this.repository = repository;
        this.clarification = clarification;
    }

    /**
     * Validates the network and turns every gap it reports into a question.
     *
     * <p>Idempotent: a gap that has already been asked about keeps the question
     * it produced, in whatever state the user left it.
     *
     * @return what was found and what was asked
     */
    public Result askAboutGaps() throws IOException {
        List<ShaclFinding> gaps = findGaps();

        Map<String, List<ClarificationService.ProposedQuestion>> byThought = new LinkedHashMap<>();
        List<String> unattributed = new ArrayList<>();

        for (ShaclFinding gap : gaps) {
            String thoughtId = repository.observingThought(gap.focusNode());
            if (thoughtId == null) {
                unattributed.add(gap.focusNode());
                continue;
            }
            byThought.computeIfAbsent(thoughtId, id -> new ArrayList<>()).add(proposal(gap));
        }

        List<ClarificationQuestion> created = new ArrayList<>();
        for (Map.Entry<String, List<ClarificationService.ProposedQuestion>> entry : byThought.entrySet()) {
            created.addAll(clarification.addQuestions(entry.getKey(), entry.getValue()));
        }

        log.info("network clarification: {} gap(s), {} question(s) asked, {} unattributed",
                gaps.size(), created.size(), unattributed.size());

        return new Result(gaps.size(), created, List.copyOf(new LinkedHashSet<>(unattributed)));
    }

    /**
     * Where one gap stands as a question, so a report can say what a reader may
     * do about each line rather than only what the shapes found.
     *
     * @param focusNode URI of the node the finding is about
     * @param path      the missing property, as SHACL reports it
     */
    public GapState stateOf(String focusNode, String path) {
        String property = strip(path);
        String thoughtId = repository.observingThought(focusNode);
        if (thoughtId == null) {
            return GapState.UNATTRIBUTED;
        }
        String questionId = QuestionIds.ofGap(focusNode, property);
        return clarification.allQuestions(thoughtId).stream()
                .filter(question -> question.id().equals(questionId))
                .findFirst()
                .map(question -> question.isOpen() ? GapState.ASKED : GapState.CLOSED)
                .orElse(GapState.WAITING);
    }

    /**
     * Every clarification candidate the shapes report, across the knowledge
     * graphs, with duplicates removed.
     *
     * <p>The same node can sit in more than one graph, and a gap found twice is
     * still one gap.
     */
    private List<ShaclFinding> findGaps() {
        Set<String> seen = new LinkedHashSet<>();
        List<ShaclFinding> gaps = new ArrayList<>();
        for (String graph : KNOWLEDGE_GRAPHS) {
            for (ShaclFinding finding : validation.validateGraph(graph).clarificationCandidates()) {
                if (finding.path() != null && seen.add(finding.focusNode() + "|" + finding.path())) {
                    gaps.add(finding);
                }
            }
        }
        return gaps;
    }

    /**
     * One gap, written as a question with the node and property it is about.
     *
     * <p>A question that expects one of a set of values is asked with that set
     * beside it, taken from the network rather than from a list kept here: the
     * layers are the ones the ontology declares, and the settings are the ones
     * earlier thoughts already named. That is what keeps a second spelling of an
     * existing context from entering the graph through an answer box.
     */
    private ClarificationService.ProposedQuestion proposal(ShaclFinding gap) {
        String property = strip(gap.path());
        GapPhrasing.Phrasing phrasing =
                GapPhrasing.of(property, repository.describe(gap.focusNode()), gap.message());
        List<String> options = phrasing.kind().offersOptions()
                ? repository.knownValuesOf(property)
                : List.of();
        return new ClarificationService.ProposedQuestion(
                QuestionIds.ofGap(gap.focusNode(), property),
                phrasing.text(),
                gap.focusNode(),
                property,
                false,
                phrasing.kind(),
                options);
    }

    /** SHACL reports a path as {@code <uri>}; the contract carries the bare URI. */
    private static String strip(String path) {
        return path.startsWith("<") && path.endsWith(">")
                ? path.substring(1, path.length() - 1)
                : path;
    }

    /**
     * What one run produced.
     *
     * @param gapsFound    how many gaps the shapes reported
     * @param asked        the questions this run added, in the order they were created
     * @param unattributed nodes with a gap that no thought is recorded as having
     *                     observed, so nothing can be asked about them
     */
    public record Result(int gapsFound,
                         List<ClarificationQuestion> asked,
                         List<String> unattributed) {
    }
}
