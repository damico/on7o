package org.on7o.server.web;

import org.on7o.server.clarification.GapState;
import org.on7o.server.clarification.NetworkClarificationService;
import org.on7o.server.hcin.HcinGraphs;
import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.hcin.ShaclFinding;
import org.on7o.server.hcin.ShaclReport;
import org.on7o.server.hcin.ShaclSeverity;
import org.on7o.server.hcin.ShaclValidationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * What the shapes have to say about the network, as a page.
 *
 * <p>The point the page has to make is that severity carries meaning. A
 * violation says the data is wrong; a clarification candidate says something is
 * missing that a person could be asked about, which is the pipeline working
 * rather than failing. A report that showed both as red rows would flatten the
 * one distinction the shapes exist to draw.
 *
 * <p>Every graph is validated on each load so the counts can sit on the tabs.
 * Where the findings are is itself informative, and finding that out should not
 * require clicking through all four.
 *
 * <p>The page also answers a narrower question: what did one thought leave
 * unresolved? A thought that has been through all three stages has put nodes in
 * the graph, and the shapes have something to say about them. Naming a thought
 * narrows every list and every count to the findings on the nodes it observed,
 * which is what makes this reachable from the thought rather than only from the
 * top of the site.
 */
@Controller
public class ValidationViewController {

    /** The graphs holding claims about the world, in the order they are shown. */
    private static final List<String> GRAPHS =
            List.of("asserted", "inferred", "hypotheses", "thoughts");

    private final ShaclValidationService validation;
    private final HcinRepository repository;
    private final NetworkClarificationService network;

    public ValidationViewController(ShaclValidationService validation,
                                    HcinRepository repository,
                                    NetworkClarificationService network) {
        this.validation = validation;
        this.repository = repository;
        this.network = network;
    }

    /**
     * Renders the validation of one graph, with the others summarized beside it.
     *
     * @param graph   short name of the graph to show
     * @param thought id of a thought to narrow the report to, or null for all of it
     */
    @GetMapping("/hcin/validation")
    public String validation(@RequestParam(defaultValue = "hypotheses") String graph,
                             @RequestParam(required = false) String thought,
                             Model model) {

        String selected = GRAPHS.contains(graph) ? graph : "hypotheses";
        Map<String, String> observers = new HashMap<>();

        List<GraphTab> tabs = new ArrayList<>();
        Findings shown = null;
        for (String name : GRAPHS) {
            ShaclReport report = validation.validateGraph(HcinGraphs.resolve(name));
            Findings findings = Findings.of(report, thought, node -> observedBy(node, observers));
            tabs.add(new GraphTab(
                    name,
                    findings.defects().size(),
                    findings.thin().size(),
                    findings.gaps().size(),
                    name.equals(selected)));
            if (name.equals(selected)) {
                shown = findings;
            }
        }

        model.addAttribute("graph", selected);
        model.addAttribute("thought", thought);
        model.addAttribute("tabs", tabs);
        model.addAttribute("usable", shown.defects().isEmpty());
        List<FindingView> gaps = gapViews(shown.gaps(), observers);
        model.addAttribute("defects", views(shown.defects(), observers));
        model.addAttribute("thin", views(shown.thin(), observers));
        model.addAttribute("gaps", gaps);
        model.addAttribute("waiting",
                gaps.stream().filter(gap -> gap.state() == GapState.WAITING).count());
        return "hcin-validation";
    }

    /**
     * The thought that observed a node, asked once per node per request.
     *
     * <p>The same node is reported by more than one shape and sits in more than
     * one graph, so without this the filter would ask the store the same question
     * several times to get the same answer.
     */
    private String observedBy(String node, Map<String, String> observers) {
        if (!observers.containsKey(node)) {
            observers.put(node, repository.observingThought(node));
        }
        return observers.get(node);
    }

    /**
     * Findings with the node made readable.
     *
     * <p>One lookup per finding, plus the observer already found while counting.
     * A graph with many findings would want them batched. It is deliberately left
     * simple until that is a real problem: the page is read by one person at a
     * time, over a network built from a few hundred thoughts.
     */
    private List<FindingView> views(List<ShaclFinding> findings, Map<String, String> observers) {
        return findings.stream()
                .map(finding -> FindingView.of(
                        finding,
                        repository.describe(finding.focusNode()),
                        observedBy(finding.focusNode(), observers),
                        null))
                .toList();
    }

    /**
     * Gaps, each carrying what a reader can do about it.
     *
     * <p>A gap that no question can be made of, and a gap whose question is
     * waiting to be asked, are different situations with the same SHACL message.
     * The report says which is which, because a reader who cannot tell will go
     * looking for an answer box that was never going to exist.
     */
    private List<FindingView> gapViews(List<ShaclFinding> findings, Map<String, String> observers) {
        return findings.stream()
                .map(finding -> FindingView.of(
                        finding,
                        repository.describe(finding.focusNode()),
                        observedBy(finding.focusNode(), observers),
                        network.stateOf(finding.focusNode(), finding.path())))
                .toList();
    }

    /**
     * One report split by severity, and narrowed to one thought when asked.
     *
     * @param defects findings that make the data untrustworthy
     * @param thin    findings that say the data is usable but incomplete
     * @param gaps    findings that are questions waiting to be asked
     */
    private record Findings(List<ShaclFinding> defects,
                            List<ShaclFinding> thin,
                            List<ShaclFinding> gaps) {

        /**
         * Splits a report, keeping only what one thought observed when a thought
         * is named.
         *
         * @param report   what the shapes said about one graph
         * @param thought  the thought to keep, or null to keep everything
         * @param observer says which thought observed a node
         */
        static Findings of(ShaclReport report, String thought, UnaryOperator<String> observer) {
            return new Findings(
                    keep(report.of(ShaclSeverity.FATAL), thought, observer),
                    keep(report.of(ShaclSeverity.WARNING), thought, observer),
                    keep(report.clarificationCandidates(), thought, observer));
        }

        private static List<ShaclFinding> keep(List<ShaclFinding> findings,
                                               String thought,
                                               UnaryOperator<String> observer) {
            if (thought == null || thought.isBlank()) {
                return findings;
            }
            return findings.stream()
                    .filter(finding -> thought.equals(observer.apply(finding.focusNode())))
                    .toList();
        }
    }
}
