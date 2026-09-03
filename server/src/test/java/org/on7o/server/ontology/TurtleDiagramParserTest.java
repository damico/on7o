package org.on7o.server.ontology;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turning a thought's Turtle into something a person can look at.
 */
class TurtleDiagramParserTest {

    private static final String PREFIXES = """
            @prefix hcin: <http://on7o.io/hcin#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            """;

    private final TurtleDiagramParser parser = new TurtleDiagramParser();

    @Test
    void namesAnAnonymousNodeByWhatItSays() {
        OntologyDiagram diagram = parser.parse(PREFIXES + """
                <urn:hcin:person:ninoska> a hcin:Person ; hcin:label "Ninoska" .
                <urn:hcin:org:expo> a hcin:Organization ; hcin:label "Expo Teleinfo" .

                _:membership1
                    a hcin:Membership ;
                    hcin:member   <urn:hcin:person:ninoska> ;
                    hcin:memberOf <urn:hcin:org:expo> ;
                    hcin:role     "CEO" .
                """);

        // The label a parser invents for a blank node is a hex string that means
        // nothing to a reader. What the node is and who it joins are in its own
        // statements, and that is a name someone can actually use.
        assertThat(diagram.nodes()).extracting(DiagramNode::label)
                .contains("Membership: Ninoska / Expo Teleinfo");
        assertThat(diagram.nodes()).extracting(DiagramNode::label)
                .noneMatch(label -> label.startsWith("_:"));
    }

    @Test
    void fallsBackToTheParsersOwnLabelWhenTheNodeSaysNothing() {
        OntologyDiagram diagram = parser.parse(PREFIXES + """
                <urn:hcin:person:ninoska> hcin:knows _:someone .
                """);

        // Honest rather than pretty: a node with nothing to say has no name to
        // give, and inventing one would tell the reader something untrue.
        assertThat(diagram.nodes()).extracting(DiagramNode::label)
                .anyMatch(label -> label.startsWith("_:"));
    }

    @Test
    void keepsNamingUriNodesByTheirShortForm() {
        OntologyDiagram diagram = parser.parse(PREFIXES + """
                <urn:hcin:person:ninoska> a hcin:Person .
                """);

        assertThat(diagram.nodes()).extracting(DiagramNode::label).contains("Person");
    }
}
