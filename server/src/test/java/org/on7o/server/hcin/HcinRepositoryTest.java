package org.on7o.server.hcin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading the HCIN: that the queries find what is there, and that a question
 * asked about a past instant is answered as of that instant.
 */
class HcinRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-24T15:00:00Z");
    private static final Instant LAST_YEAR = Instant.parse("2025-08-24T15:00:00Z");

    private HcinFixture hcin;
    private HcinRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        hcin = new HcinFixture();
        repository = hcin.repository();

        hcin.loadAsserted("""
                me:me   a hcin:Person ; hcin:label "Me" .
                me:bob  a hcin:Person ; hcin:label "Bob" .
                me:sam  a hcin:Person ; hcin:label "Sam" .
                org:acme a hcin:Organization ; hcin:label "ACME Company" .

                ev:membership-bob a hcin:Membership ;
                    hcin:member    me:bob ;
                    hcin:memberOf  org:acme ;
                    hcin:role      "Account manager" ;
                    hcin:validFrom "2024-01-01T00:00:00.000Z"^^xsd:dateTime .

                ev:lunch a hcin:Interaction ;
                    hcin:participant     me:me , me:bob ;
                    hcin:interactionType "meeting" ;
                    hcin:context         org:acme ;
                    hcin:occurredAt      "2026-08-24T12:00:00.000Z"^^xsd:dateTime .

                ev:old-call a hcin:Interaction ;
                    hcin:participant     me:me , me:sam ;
                    hcin:interactionType "phoneCall" ;
                    hcin:occurredAt      "2025-01-10T12:00:00.000Z"^^xsd:dateTime .

                ev:payment a hcinf:FinancialFlow ;
                    hcinf:flowSource me:me ;
                    hcinf:flowTarget org:acme ;
                    hcinf:direction  hcinf:Outflow ;
                    hcinf:amount     "300000.00"^^xsd:decimal ;
                    hcinf:currency   "BRL" ;
                    hcin:occurredAt  "2026-08-18T10:00:00.000Z"^^xsd:dateTime .

                ev:authority-bob a hcinf:FinancialAuthority ;
                    hcinf:holder        me:bob ;
                    hcinf:organization  org:acme ;
                    hcinf:authorityType hcinf:ExpenditureAuthority ;
                    hcin:validFrom      "2026-01-01T00:00:00.000Z"^^xsd:dateTime .
                """);
    }

    @Test
    void findsPeopleAndOrganizations() {
        assertThat(repository.people()).extracting(HcinEntity::label)
                .containsExactlyInAnyOrder("Me", "Bob", "Sam");
        assertThat(repository.organizations()).extracting(HcinEntity::label)
                .containsExactly("ACME Company");
        assertThat(repository.people()).allSatisfy(person ->
                assertThat(person.tier()).isEqualTo(KnowledgeTier.ASSERTED));
    }

    @Test
    void findsMembershipsThatHoldAtTheGivenInstant() {
        assertThat(repository.memberships(NOW)).singleElement().satisfies(membership -> {
            assertThat(membership.personUri()).isEqualTo("urn:hcin:person:bob");
            assertThat(membership.organizationUri()).isEqualTo("urn:hcin:org:acme");
            assertThat(membership.role()).isEqualTo("Account manager");
        });
    }

    @Test
    void findsOnlyTheInteractionsThatHadHappenedYet() {
        List<HcinInteraction> now = repository.interactionsWithEgo(NOW);
        assertThat(now).extracting(HcinInteraction::otherUri)
                .containsExactlyInAnyOrder("urn:hcin:person:bob", "urn:hcin:person:sam");

        List<HcinInteraction> lastYear = repository.interactionsWithEgo(LAST_YEAR);
        assertThat(lastYear).extracting(HcinInteraction::otherUri)
                .containsExactly("urn:hcin:person:sam");
    }

    @Test
    void neverReturnsTheEgoAsItsOwnCounterpart() {
        assertThat(repository.interactionsWithEgo(NOW))
                .extracting(HcinInteraction::otherUri)
                .doesNotContain(hcin.ego());
    }

    @Test
    void findsFinancialFlowsWithTheirAmountsIntact() {
        assertThat(repository.flowsInvolvingEgo(NOW)).singleElement().satisfies(flow -> {
            assertThat(flow.direction()).isEqualTo(FlowDirection.OUTFLOW);
            assertThat(flow.amount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(flow.currency()).isEqualTo("BRL");
            assertThat(flow.counterparty(hcin.ego())).isEqualTo("urn:hcin:org:acme");
        });
    }

    @Test
    void doesNotReportAuthorityBeforeItBegan() {
        assertThat(repository.authorities(NOW)).singleElement().satisfies(authority -> {
            assertThat(authority.holderUri()).isEqualTo("urn:hcin:person:bob");
            assertThat(authority.type()).isEqualTo(AuthorityType.EXPENDITURE);
            assertThat(authority.scope()).isNull();
        });

        assertThat(repository.authorities(LAST_YEAR)).isEmpty();
    }

    @Test
    void tellsAHypothesisApartFromAFact() {
        hcin.load(HcinGraphs.HYPOTHESES, """
                me:carol a hcin:Person ; hcin:label "Carol" .
                """);

        assertThat(repository.people())
                .filteredOn(person -> "Carol".equals(person.label()))
                .singleElement()
                .satisfies(carol -> assertThat(carol.tier()).isEqualTo(KnowledgeTier.HYPOTHESIZED));
    }

    @Test
    void exportsAGraphAsTurtleAndKnowsHowBigItIs() {
        assertThat(repository.size(HcinGraphs.ASSERTED)).isPositive();

        String turtle = repository.export(HcinGraphs.ASSERTED);
        assertThat(turtle).contains("ACME Company").contains("hcinf:");

        assertThat(repository.exportAll()).contains("HCIN core");
    }

    @Test
    void knowsWhetherItHasHeardOfSomething() {
        assertThat(repository.exists("urn:hcin:person:bob")).isTrue();
        assertThat(repository.exists("urn:hcin:person:nobody")).isFalse();
    }

    @Test
    void keepsWhatItWasToldAfterTheProcessThatToldItIsGone(@org.junit.jupiter.api.io.TempDir
                                                           java.nio.file.Path location) throws IOException {
        HcinProperties properties = new HcinProperties();
        properties.setLocation(location.toString());

        HcinDataset first = new HcinDataset(properties);
        new HcinRepository(new HcinTransactions(first), first)
                .add(HcinGraphs.ASSERTED, org.apache.jena.rdf.model.ModelFactory.createDefaultModel()
                        .add(org.apache.jena.rdf.model.ResourceFactory.createStatement(
                                org.apache.jena.rdf.model.ResourceFactory.createResource("urn:hcin:person:bob"),
                                HcinVocabulary.LABEL,
                                org.apache.jena.rdf.model.ResourceFactory.createPlainLiteral("Bob"))));
        first.close();

        HcinDataset reopened = new HcinDataset(properties);
        HcinRepository afterRestart = new HcinRepository(new HcinTransactions(reopened), reopened);

        assertThat(afterRestart.export(HcinGraphs.ASSERTED)).contains("Bob");
        reopened.close();
    }

    @Test
    void loadsTheSchemaIntoItsOwnGraph() {
        assertThat(repository.size(HcinGraphs.SCHEMA)).isPositive();
        assertThat(repository.export(HcinGraphs.SCHEMA)).contains("Financial authority");
    }

    @Test
    void knowsWhichNamesTheVocabularyAlreadyDefines() {
        // The entity scan reads one consolidated thought in isolation and reports
        // terms it was used without being defined. The pipeline's own stamps look
        // exactly like that, and asking the user what "Asserted" means is asking
        // them about the tool rather than about their life.
        assertThat(repository.definesTerm("Asserted")).isTrue();
        assertThat(repository.definesTerm("hypothesized")).isTrue();
        assertThat(repository.definesTerm("knowledgeStatus")).isTrue();
        assertThat(repository.definesTerm("Social relationship")).isTrue();

        assertThat(repository.definesTerm("Ninoska")).isFalse();
        assertThat(repository.definesTerm("Expo Teleinfo")).isFalse();
    }

    @Test
    void listsEveryThoughtThatMentionedANode() {
        hcin.load(HcinGraphs.PROVENANCE, """
                <urn:hcin:observation:o1> a hcin:Observation ;
                    hcin:about      <urn:hcin:person:ninoska> ;
                    hcin:thoughtId  "t-second" ;
                    hcin:observedAt "2026-08-24T15:30:00Z"^^xsd:dateTime .
                <urn:hcin:observation:o2> a hcin:Observation ;
                    hcin:about      <urn:hcin:person:ninoska> ;
                    hcin:thoughtId  "t-first" ;
                    hcin:observedAt "2026-08-01T09:00:00Z"^^xsd:dateTime .
                <urn:hcin:observation:o3> a hcin:Observation ;
                    hcin:about     <urn:hcin:person:someone-else> ;
                    hcin:thoughtId "t-other" .
                """);

        // A person on a projection is a name and a few numbers, and the way back
        // to what was actually said is every thought that mentioned them, oldest
        // first, not only the one that introduced them.
        assertThat(repository.thoughtsMentioning("urn:hcin:person:ninoska"))
                .containsExactly("t-first", "t-second");
    }

    @Test
    void readsTheAlignmentsTheOntologyDeclares() {
        // HCIN.md promises reuse of FOAF, PROV-O and the W3C organization
        // vocabulary. hcin-core.ttl now says which terms are the same ones, and
        // the pipeline reads that back instead of keeping a list in the code.
        assertThat(repository.vocabularyAliases())
                .containsEntry("http://xmlns.com/foaf/0.1/Person", "http://on7o.io/hcin#Person")
                .containsEntry("http://www.w3.org/ns/org#Membership", "http://on7o.io/hcin#Membership")
                .containsEntry("http://www.w3.org/ns/org#organization", "http://on7o.io/hcin#memberOf")
                .containsEntry("http://www.w3.org/ns/prov#wasDerivedFrom",
                        "http://on7o.io/hcin#wasDerivedFrom");

        // A subproperty is not a synonym: rewriting every rdfs:label into
        // hcin:label would rename classes and properties too.
        assertThat(repository.vocabularyAliases())
                .doesNotContainKey("http://www.w3.org/2000/01/rdf-schema#label");
    }
}
