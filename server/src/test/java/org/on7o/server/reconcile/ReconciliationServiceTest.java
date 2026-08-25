package org.on7o.server.reconcile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.on7o.server.hcin.HcinFixture;
import org.on7o.server.hcin.HcinGraphs;
import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.ingest.StorageProperties;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtService;
import org.on7o.server.ingest.ThoughtStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Merging a consolidated thought into the network: that it finds the same
 * entities twice, that it does not promote a guess by merging it, and that one
 * approval does not become standing power.
 */
class ReconciliationServiceTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-24T15:30:00Z");

    private static final String CTHOUGHT = """
            @prefix on7o:  <http://on7o.io/ontology#> .
            @prefix hcin:  <http://on7o.io/hcin#> .
            @prefix hcinf: <http://on7o.io/hcin/financial#> .
            @prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .

            on7o:Bob         a on7o:Person       ; rdfs:label "Bob" .
            on7o:Maria       a on7o:Person       ; rdfs:label "Maria" .
            on7o:AcmeCompany a on7o:Organization ; rdfs:label "ACME Company" .
            on7o:AbcCompany  a on7o:Organization ; rdfs:label "ABC Company" .

            on7o:Bob on7o:worksAt on7o:AcmeCompany .
            << on7o:Bob on7o:worksAt on7o:AcmeCompany >>
                    on7o:knowledgeStatus on7o:Asserted ;
                    on7o:confidence      0.9 .

            on7o:Maria on7o:mayKnow on7o:Bob .
            << on7o:Maria on7o:mayKnow on7o:Bob >>
                    on7o:knowledgeStatus on7o:Hypothesized ;
                    on7o:confidence      0.3 .

            on7o:lunch a hcin:Interaction ;
                hcin:participant     <urn:hcin:person:me> , on7o:Bob ;
                hcin:interactionType "meeting" ;
                hcin:occurredAt      "2026-08-24T12:00:00.000Z"^^xsd:dateTime ;
                hcin:knowledgeStatus hcin:Asserted .

            on7o:approval a hcinf:FinancialAuthority ;
                hcinf:holder         on7o:Maria ;
                hcinf:organization   on7o:AbcCompany ;
                hcinf:authorityType  hcinf:ExpenditureAuthority ;
                hcin:knowledgeStatus hcin:Asserted .
            """;

    private HcinFixture hcin;
    private HcinRepository repository;
    private ThoughtStore store;
    private ReconciliationService reconciliation;
    private String thoughtId;

    @BeforeEach
    void setUp(@TempDir Path root) throws IOException {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(root.toString());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        store = new ThoughtStore(properties, mapper);

        hcin = new HcinFixture();
        repository = hcin.repository();
        reconciliation = new ReconciliationService(
                store, new CThoughtReader(), new EntityMatcher(repository), repository);

        thoughtId = new ThoughtService(store)
                .ingestText("Almocei com o Bob.", CAPTURED_AT, Thought.SOURCE_SYNTHETIC, null)
                .id();
    }

    @Test
    void createsEveryEntityTheThoughtNames() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);

        ReconciliationResult result = reconciliation.reconcile(thoughtId);

        assertThat(result.status()).isEqualTo(ReconciliationStatus.RECONCILED);
        assertThat(result.entitiesCreated()).isEqualTo(4);
        assertThat(result.entitiesMatched()).isZero();

        assertThat(repository.people()).extracting(entity -> entity.label())
                .contains("Bob", "Maria");
        assertThat(repository.organizations()).extracting(entity -> entity.label())
                .contains("ACME Company", "ABC Company");
    }

    @Test
    void mintsTheSameUriForTheSamePersonHoweverTheyAreWritten() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        assertThat(repository.exists("urn:hcin:person:bob")).isTrue();
        assertThat(repository.exists("urn:hcin:org:acme-company")).isTrue();
    }

    @Test
    void addsNothingWhenTheSameThoughtIsReconciledAgain() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        long asserted = repository.size(HcinGraphs.ASSERTED);
        long hypotheses = repository.size(HcinGraphs.HYPOTHESES);
        long provenance = repository.size(HcinGraphs.PROVENANCE);

        ReconciliationResult again = reconciliation.reconcile(thoughtId);

        assertThat(again.entitiesCreated()).isZero();
        assertThat(again.entitiesMatched()).isEqualTo(4);
        assertThat(again.statementsAsserted()).isZero();
        assertThat(again.statementsHypothesized()).isZero();

        assertThat(repository.size(HcinGraphs.ASSERTED)).isEqualTo(asserted);
        assertThat(repository.size(HcinGraphs.HYPOTHESES)).isEqualTo(hypotheses);
        assertThat(repository.size(HcinGraphs.PROVENANCE)).isEqualTo(provenance);
    }

    @Test
    void keepsAGuessAGuessAndAFactAFact() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        String asserted = repository.export(HcinGraphs.ASSERTED);
        String hypotheses = repository.export(HcinGraphs.HYPOTHESES);

        assertThat(asserted).contains("worksAt");
        assertThat(asserted).doesNotContain("mayKnow");
        assertThat(hypotheses).contains("mayKnow");
    }

    @Test
    void refusesToTurnOneApprovalIntoStandingPower() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        // The thought asserted the authority. It said nothing about its scope,
        // so it is recorded as a hypothesis rather than as a fact about Maria.
        assertThat(repository.export(HcinGraphs.HYPOTHESES)).contains("FinancialAuthority");
        assertThat(repository.export(HcinGraphs.ASSERTED)).doesNotContain("FinancialAuthority");

        assertThat(repository.authorities(Instant.parse("2026-08-25T00:00:00Z")))
                .singleElement()
                .satisfies(authority -> {
                    assertThat(authority.holderUri()).isEqualTo("urn:hcin:person:maria");
                    assertThat(authority.scope()).isNull();
                    assertThat(authority.validTo()).isNull();
                });
    }

    @Test
    void carriesInteractionsOverWithTheirParticipantsRewritten() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        assertThat(repository.interactionsWithEgo(Instant.parse("2026-08-25T00:00:00Z")))
                .singleElement()
                .satisfies(interaction -> {
                    assertThat(interaction.otherUri()).isEqualTo("urn:hcin:person:bob");
                    assertThat(interaction.type()).isEqualTo("meeting");
                    assertThat(interaction.occurredAt())
                            .isEqualTo(Instant.parse("2026-08-24T12:00:00Z"));
                });
    }

    @Test
    void marksTheLayerOfWhatItCanAndLeavesTheRestUnmarked() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        // Power over money is financial by construction, so it needs no guessing.
        assertThat(repository.export(HcinGraphs.HYPOTHESES))
                .contains("hcin:layer").contains("hcin:Financial");

        // A plain claim between two people says nothing about which layer it
        // lives in, and inventing one would be indistinguishable from knowing.
        assertThat(repository.export(HcinGraphs.ASSERTED)).doesNotContain("hcin:layer");
    }

    @Test
    void recordsWhereEverythingCameFrom() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        String provenance = repository.export(HcinGraphs.PROVENANCE);
        assertThat(provenance).contains("Observation").contains(thoughtId);

        String thoughts = repository.export(HcinGraphs.THOUGHTS);
        assertThat(thoughts).contains(thoughtId).contains("Almocei com o Bob.");

        assertThat(repository.export(HcinGraphs.ASSERTED))
                .contains("wasDerivedFrom")
                .contains("observedAt");
    }

    @Test
    void doesNotOverwriteWhatAnotherThoughtAlreadyEstablished() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        String contradiction = """
                @prefix on7o: <http://on7o.io/ontology#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

                on7o:Bob         a on7o:Person       ; rdfs:label "Bob" .
                on7o:AcmeCompany a on7o:Organization ; rdfs:label "ACME Company" .

                on7o:Bob on7o:worksAt on7o:AcmeCompany .
                << on7o:Bob on7o:worksAt on7o:AcmeCompany >>
                        on7o:knowledgeStatus on7o:Hypothesized ;
                        on7o:confidence      0.2 .
                """;

        String secondThought = new ThoughtService(store)
                .ingestText("Acho que o Bob saiu da ACME.", CAPTURED_AT, Thought.SOURCE_SYNTHETIC, null)
                .id();
        store.saveConsolidatedThought(secondThought, contradiction);

        reconciliation.reconcile(secondThought);

        // The doubt is recorded beside the fact, not on top of it.
        assertThat(repository.export(HcinGraphs.ASSERTED)).contains("worksAt");
        assertThat(repository.export(HcinGraphs.HYPOTHESES)).contains("worksAt");
    }

    @Test
    void doesNotLetAThoughtDecideHowStronglyItsOwnClaimsAreHeld() throws IOException {
        store.saveConsolidatedThought(thoughtId, CTHOUGHT);
        reconciliation.reconcile(thoughtId);

        // The thought marked its unscoped authority as asserted. It landed among
        // the hypotheses, and it says so: a node may not claim both.
        String hypotheses = repository.export(HcinGraphs.HYPOTHESES);
        assertThat(hypotheses).contains("hcin:Hypothesized");
        assertThat(hypotheses).doesNotContain("hcin:Asserted");
    }

    @Test
    void refusesToReconcileAThoughtThatWasNeverConsolidated() {
        assertThatThrownBy(() -> reconciliation.reconcile(thoughtId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not been consolidated");
    }

    @Test
    void reportsNothingToMergeWhenTheThoughtSaidNothingUsable() throws IOException {
        store.saveConsolidatedThought(thoughtId, "@prefix on7o: <http://on7o.io/ontology#> .\n");

        assertThat(reconciliation.reconcile(thoughtId).status())
                .isEqualTo(ReconciliationStatus.NOTHING_TO_MERGE);
    }
}
