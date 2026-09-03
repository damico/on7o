package org.on7o.server.hcin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the shapes are for: catching data that is wrong, and finding the gaps
 * worth asking a person about.
 */
class ShaclValidationServiceTest {

    private HcinFixture hcin;
    private ShaclValidationService validation;

    @BeforeEach
    void setUp() throws IOException {
        hcin = new HcinFixture();
        validation = new ShaclValidationService(hcin.repository());
    }

    @Test
    void acceptsAWellFormedFlow() {
        ShaclReport report = validate("""
                me:me    a hcin:Person ; hcin:label "Me" .
                org:acme a hcin:Organization ; hcin:label "ACME" .

                ev:payment a hcinf:FinancialFlow ;
                    hcinf:flowSource     me:me ;
                    hcinf:flowTarget     org:acme ;
                    hcinf:direction      hcinf:Outflow ;
                    hcinf:amount         "300000.00"^^xsd:decimal ;
                    hcinf:currency       "BRL" ;
                    hcin:occurredAt      "2026-08-18T10:00:00.000Z"^^xsd:dateTime ;
                    hcin:wasDerivedFrom  <urn:hcin:thought:t1> ;
                    hcin:knowledgeStatus hcin:Asserted ;
                    hcin:observedAt      "2026-08-18T10:00:00.000Z"^^xsd:dateTime .
                """);

        assertThat(report.of(ShaclSeverity.FATAL)).isEmpty();
        assertThat(report.isUsable()).isTrue();
    }

    @Test
    void asksAboutAnAmountWithNoCurrency() {
        ShaclReport report = validate("""
                me:me    a hcin:Person ; hcin:label "Me" .
                org:acme a hcin:Organization ; hcin:label "ACME" .

                ev:payment a hcinf:FinancialFlow ;
                    hcinf:flowSource me:me ;
                    hcinf:flowTarget org:acme ;
                    hcinf:direction  hcinf:Outflow ;
                    hcinf:amount     "300000.00"^^xsd:decimal ;
                    hcin:occurredAt  "2026-08-18T10:00:00.000Z"^^xsd:dateTime .
                """);

        // Consolidation is told never to invent a currency the thought did not
        // state, so an absent one is a gap to ask about. The data is still usable.
        assertThat(report.conforms()).isFalse();
        assertThat(report.isUsable()).isTrue();
        assertThat(report.clarificationCandidates())
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("currency of this flow is unknown"));
    }

    @Test
    void rejectsACurrencyThatIsNotAnIsoCode() {
        ShaclReport report = validate("""
                me:me    a hcin:Person ; hcin:label "Me" .
                org:acme a hcin:Organization ; hcin:label "ACME" .

                ev:payment a hcinf:FinancialFlow ;
                    hcinf:flowSource me:me ;
                    hcinf:flowTarget org:acme ;
                    hcinf:direction  hcinf:Outflow ;
                    hcinf:amount     "300000.00"^^xsd:decimal ;
                    hcinf:currency   "reais" ;
                    hcin:occurredAt  "2026-08-18T10:00:00.000Z"^^xsd:dateTime .
                """);

        // Absent is a question; malformed is an error. A currency nothing can be
        // compared against is wrong data, not missing data.
        assertThat(report.isUsable()).isFalse();
        assertThat(report.of(ShaclSeverity.FATAL))
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("ISO 4217"));
    }

    @Test
    void asksWhenAnInteractionWithNoTimestampHappened() {
        ShaclReport report = validate("""
                me:me  a hcin:Person ; hcin:label "Me" .
                me:bob a hcin:Person ; hcin:label "Bob" .

                ev:lunch a hcin:Interaction ;
                    hcin:participant me:me , me:bob .
                """);

        // The consolidation prompt forbids inventing a date the thought did not
        // state. Demanding one here would have the two rules contradict each
        // other, one requiring exactly what the other forbids, which is what a
        // real capture exposed: a lunch nobody dated became a fatal defect.
        assertThat(report.isUsable()).isTrue();
        assertThat(report.clarificationCandidates())
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("no date"));
    }

    @Test
    void rejectsAnInteractionDatedTwice() {
        ShaclReport report = validate("""
                me:me  a hcin:Person ; hcin:label "Me" .
                me:bob a hcin:Person ; hcin:label "Bob" .

                ev:lunch a hcin:Interaction ;
                    hcin:participant me:me , me:bob ;
                    hcin:occurredAt  "2026-08-18T10:00:00.000Z"^^xsd:dateTime ,
                                     "2026-08-19T10:00:00.000Z"^^xsd:dateTime .
                """);

        // An interaction happened once. Two dates is not a gap in what is known,
        // it is a claim that cannot be true.
        assertThat(report.of(ShaclSeverity.FATAL))
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("happened once"));
    }

    @Test
    void refusesAFactThatStopsBeingTrueBeforeItStarts() {
        ShaclReport report = validate("""
                me:bob a hcin:Person ; hcin:label "Bob" .

                ev:authority a hcinf:FinancialAuthority ;
                    hcinf:holder   me:bob ;
                    hcin:validFrom "2026-06-01T00:00:00.000Z"^^xsd:dateTime ;
                    hcin:validTo   "2026-01-01T00:00:00.000Z"^^xsd:dateTime .
                """);

        assertThat(report.of(ShaclSeverity.FATAL))
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("validFrom must not be later"));
    }

    @Test
    void treatsMissingAuthorityScopeAsSomethingToAskAbout() {
        ShaclReport report = validate("""
                me:maria a hcin:Person ; hcin:label "Maria" .
                org:abc  a hcin:Organization ; hcin:label "ABC" .

                ev:authority a hcinf:FinancialAuthority ;
                    hcinf:holder         me:maria ;
                    hcinf:organization   org:abc ;
                    hcinf:authorityType  hcinf:ExpenditureAuthority ;
                    hcin:wasDerivedFrom  <urn:hcin:thought:t1> ;
                    hcin:knowledgeStatus hcin:Hypothesized ;
                    hcin:observedAt      "2026-08-18T10:00:00.000Z"^^xsd:dateTime ;
                    hcin:validFrom       "2026-08-18T10:00:00.000Z"^^xsd:dateTime .
                """);

        assertThat(report.isUsable()).isTrue();
        assertThat(report.clarificationCandidates())
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("scope of this authority is unknown"));
    }

    @Test
    void warnsAboutAStatementWithNoEvidenceBehindIt() {
        ShaclReport report = validate("""
                me:me  a hcin:Person ; hcin:label "Me" .
                me:bob a hcin:Person ; hcin:label "Bob" .

                ev:lunch a hcin:Interaction ;
                    hcin:participant     me:me , me:bob ;
                    hcin:interactionType "meeting" ;
                    hcin:occurredAt      "2026-08-24T12:00:00.000Z"^^xsd:dateTime .
                """);

        assertThat(report.of(ShaclSeverity.WARNING))
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("no evidence behind it"));
    }

    @Test
    void validatesAGraphOfTheDatasetInPlace() {
        hcin.loadAsserted("""
                me:bob a hcin:Person .
                """);

        assertThat(validation.validateGraph(HcinGraphs.ASSERTED).of(ShaclSeverity.FATAL))
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("the name the ego knows them by"));
    }

    @Test
    void asksWhichLayerAndSettingATieBetweenPeopleBelongsTo() {
        ShaclReport report = validate("""
                me:me      a hcin:Person ; hcin:label "Me" .
                me:ninoska a hcin:Person ; hcin:label "Ninoska" .

                ev:invite a hcin:Relationship, hcin:SocialRelationship ;
                    hcin:source me:me ;
                    hcin:target me:ninoska .
                """);

        assertThat(report.clarificationCandidates())
                .extracting(ShaclFinding::path)
                .containsExactlyInAnyOrder(
                        "<http://on7o.io/hcin#layer>", "<http://on7o.io/hcin#context>");
    }

    @Test
    void asksNeitherOfAVenueStandingInACity() {
        ShaclReport report = validate("""
                ev:venue a hcin:Entity ; hcin:label "Salon Guarayo" .
                ev:city  a hcin:Entity ; hcin:label "Santa Cruz" .

                ev:located a hcin:Relationship ;
                    hcin:source ev:venue ;
                    hcin:target ev:city .
                """);

        // Reconciliation mints a relationship for every statement a thought makes,
        // and only the ones joining people or organizations are typed social. The
        // rest are perfectly good data with no layer to name and no setting to
        // give, and a report that listed them would bury the ones worth reading.
        assertThat(report.clarificationCandidates()).isEmpty();
    }

    private ShaclReport validate(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFParser.fromString(HcinFixture.prefixed(turtle)).lang(Lang.TURTLE).parse(model);
        return validation.validate(model);
    }
}
