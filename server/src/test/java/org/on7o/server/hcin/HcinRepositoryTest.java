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
}
