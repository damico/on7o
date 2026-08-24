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
    void rejectsAnAmountWithoutACurrency() {
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

        assertThat(report.conforms()).isFalse();
        assertThat(report.isUsable()).isFalse();
        assertThat(report.of(ShaclSeverity.FATAL))
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("ISO 4217"));
    }

    @Test
    void rejectsAnInteractionWithNoTimestamp() {
        ShaclReport report = validate("""
                me:me  a hcin:Person ; hcin:label "Me" .
                me:bob a hcin:Person ; hcin:label "Bob" .

                ev:lunch a hcin:Interaction ;
                    hcin:participant me:me , me:bob .
                """);

        assertThat(report.of(ShaclSeverity.FATAL))
                .extracting(ShaclFinding::message)
                .anyMatch(message -> message.contains("when it occurred"));
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

    private ShaclReport validate(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFParser.fromString(HcinFixture.prefixed(turtle)).lang(Lang.TURTLE).parse(model);
        return validation.validate(model);
    }
}
