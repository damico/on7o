package org.on7o.server.reconcile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.on7o.server.hcin.HcinFixture;
import org.on7o.server.hcin.KnowledgeTier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading a consolidated thought for what it says about the world.
 */
class CThoughtReaderTest {

    private static final String PREFIXES = """
            @prefix on7o: <http://on7o.io/ontology#> .
            @prefix hcin: <http://on7o.io/hcin#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix foaf: <http://xmlns.com/foaf/0.1/> .
            @prefix org:  <http://www.w3.org/ns/org#> .
            """;

    private HcinFixture hcin;
    private CThoughtReader reader;

    @BeforeEach
    void setUp() throws IOException {
        // A real schema graph, because what the reader recognizes is decided by
        // the alignments the ontology declares rather than by a list in the code.
        hcin = new HcinFixture();
        reader = new CThoughtReader(hcin.repository());
    }

    @Test
    void readsAnEntityTheThoughtActuallyDescribed() {
        CandidateKnowledge knowledge = reader.read(PREFIXES + """
                on7o:Bob a on7o:Person ; rdfs:label "Bob" .
                """);

        assertThat(knowledge.entities()).extracting(CandidateEntity::label).containsExactly("Bob");
        assertThat(knowledge.entities()).extracting(CandidateEntity::kind)
                .containsExactly(EntityKind.PERSON);
    }

    @Test
    void skipsATermThatWasOnlyDeclaredToBeAnIndividual() {
        CandidateKnowledge knowledge = reader.read(PREFIXES + """
                on7o:Asserted a owl:NamedIndividual ; rdfs:label "Asserted" .
                """);

        // Being an individual rather than a class is a fact about the ontology.
        // Nothing here says what Asserted is in the world, so the network has
        // nothing to record, and recording it as a person would be worse.
        assertThat(knowledge.entities()).isEmpty();
    }

    @Test
    void stillReadsAnIndividualThatWasAlsoSaidToBeSomething() {
        CandidateKnowledge knowledge = reader.read(PREFIXES + """
                on7o:Ninoska a owl:NamedIndividual, on7o:Person ; rdfs:label "Ninoska" .
                """);

        assertThat(knowledge.entities()).extracting(CandidateEntity::kind)
                .containsExactly(EntityKind.PERSON);
    }

    @Test
    void keepsAMembershipTheThoughtLeftAnonymous() {
        CandidateKnowledge knowledge = reader.read(PREFIXES + """
                _:membership1
                    a hcin:Membership ;
                    hcin:member   <urn:hcin:person:ninoska> ;
                    hcin:memberOf <urn:hcin:organization:expoteleinfo> ;
                    hcin:role     "CEO" .
                """);

        // The W3C organization vocabulary recommends this n-ary shape for a
        // person holding a role in an organization, and writing it anonymously is
        // the idiomatic way. Dropping it lost the fact that someone runs a
        // company, silently, because nothing downstream can hold a blank node.
        assertThat(knowledge.events().listStatements(null, RDFS_ROLE, (org.apache.jena.rdf.model.RDFNode) null)
                .toList()).hasSize(1);
        assertThat(knowledge.events().listSubjects().toList())
                .allSatisfy(subject -> assertThat(subject.isURIResource()).isTrue());
    }

    @Test
    void namesAnAnonymousNodeFromWhatItSaysSoItSurvivesASecondReading() {
        String turtle = PREFIXES + """
                _:membership1
                    a hcin:Membership ;
                    hcin:member   <urn:hcin:person:ninoska> ;
                    hcin:memberOf <urn:hcin:organization:expoteleinfo> .
                """;

        String first = reader.read(turtle).events().listSubjects().next().getURI();
        String again = reader.read(turtle).events().listSubjects().next().getURI();

        // The label a parser gives a blank node is invented afresh every read, so
        // a URI built on it would make a new membership on every reconciliation.
        assertThat(first).isEqualTo(again);
    }

    @Test
    void readsTheStandardVocabulariesAsTheSameTerms() {
        CandidateKnowledge knowledge = reader.read(PREFIXES + """
                <urn:hcin:person:ninoska> a foaf:Person ; rdfs:label "Ninoska" .
                <urn:hcin:organization:expoteleinfo> a org:Organization ; rdfs:label "Expo Teleinfo" .
                _:membership1
                    a org:Membership ;
                    org:member       <urn:hcin:person:ninoska> ;
                    org:organization <urn:hcin:organization:expoteleinfo> ;
                    org:role         "CEO" .
                """);

        // HCIN.md says the model reuses FOAF and the W3C organization vocabulary,
        // and hcin-core.ttl now says which terms are the same. A thought written
        // in those terms is read, not discarded.
        assertThat(knowledge.entities()).extracting(CandidateEntity::kind)
                .containsExactlyInAnyOrder(EntityKind.PERSON, EntityKind.ORGANIZATION);
        assertThat(knowledge.events().listStatements(null, RDFS_ROLE, (org.apache.jena.rdf.model.RDFNode) null)
                .toList()).hasSize(1);
    }

    /** hcin:role, which org:role is read as. */
    private static final org.apache.jena.rdf.model.Property RDFS_ROLE =
            org.apache.jena.rdf.model.ResourceFactory.createProperty("http://on7o.io/hcin#role");

    @Test
    void readsHowAStatementIsKnownFromItsOwnAnnotation() {
        CandidateKnowledge knowledge = reader.read(PREFIXES + """
                on7o:Bob a on7o:Person ; rdfs:label "Bob" .
                on7o:Acme a on7o:Organization ; rdfs:label "ACME" .

                on7o:Bob on7o:worksAt on7o:Acme .
                << on7o:Bob on7o:worksAt on7o:Acme >>
                        on7o:knowledgeStatus on7o:Asserted ;
                        on7o:confidence      0.9 .
                """);

        // How something is known is a claim about a claim, which is what RDF-star
        // is for. Said this way, the statement carries its own epistemic status.
        assertThat(knowledge.statements()).singleElement()
                .satisfies(statement -> {
                    assertThat(statement.tier()).isEqualTo(KnowledgeTier.ASSERTED);
                    assertThat(statement.confidence()).isEqualByComparingTo("0.9");
                });
    }

    @Test
    void readsAnEntityTagAsTheTierOfWhatItStates() {
        CandidateKnowledge knowledge = reader.read(PREFIXES + """
                on7o:Bob a on7o:Person ; rdfs:label "Bob" ;
                        on7o:knowledgeStatus on7o:Asserted ;
                        on7o:confidence      0.8 .
                on7o:Acme a on7o:Organization ; rdfs:label "ACME" .

                on7o:Bob on7o:worksAt on7o:Acme .
                """);

        // Thoughts consolidated before the prompt asked for RDF-star tagged the
        // entity instead. Strictly that says nothing, since an entity is not a
        // claim; read as it was plainly meant, it beats filing everything the user
        // asserted as a hypothesis.
        assertThat(knowledge.statements()).singleElement()
                .satisfies(statement -> assertThat(statement.tier())
                        .isEqualTo(KnowledgeTier.ASSERTED));
    }

    @Test
    void prefersTheStatementsOwnAnnotationOverTheEntityTag() {
        CandidateKnowledge knowledge = reader.read(PREFIXES + """
                on7o:Bob a on7o:Person ; rdfs:label "Bob" ;
                        on7o:knowledgeStatus on7o:Asserted .
                on7o:Acme a on7o:Organization ; rdfs:label "ACME" .

                on7o:Bob on7o:mayAdvise on7o:Acme .
                << on7o:Bob on7o:mayAdvise on7o:Acme >>
                        on7o:knowledgeStatus on7o:Hypothesized .
                """);

        // The guess about the statement is what the writer said about that
        // statement. A tag on the entity is a weaker reading of a vaguer thing and
        // must never overrule it.
        assertThat(knowledge.statements()).singleElement()
                .satisfies(statement -> assertThat(statement.tier())
                        .isEqualTo(KnowledgeTier.HYPOTHESIZED));
    }
}
