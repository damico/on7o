package org.on7o.server.clarification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.on7o.server.hcin.HcinFixture;
import org.on7o.server.hcin.HcinGraphs;
import org.on7o.server.hcin.ShaclValidationService;
import org.on7o.server.ingest.StorageProperties;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtService;
import org.on7o.server.ingest.ThoughtStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The network asking about its own gaps.
 *
 * <p>Every other question in on7o starts from something the user said. These
 * start from what the shapes know an HCIN statement is meant to say, so what is
 * under test is whether a gap becomes a question a person can actually answer,
 * attached to the conversation it came from.
 */
class NetworkClarificationServiceTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-24T15:30:00Z");

    private HcinFixture hcin;
    private ClarificationService clarification;
    private NetworkClarificationService network;
    private String thoughtId;

    @BeforeEach
    void setUp(@TempDir Path root) throws IOException {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(root.toString());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ThoughtStore store = new ThoughtStore(properties, mapper);

        hcin = new HcinFixture();
        clarification = new ClarificationService(new ClarificationStore(store, mapper));
        network = new NetworkClarificationService(
                new ShaclValidationService(hcin.repository()), hcin.repository(), clarification);

        thoughtId = new ThoughtService(store)
                .ingestText("A Ninoska me convidou.", CAPTURED_AT, Thought.SOURCE_SYNTHETIC, null)
                .id();
    }

    /**
     * Two people, a relationship between them with neither layer nor context, and
     * the provenance saying which thought said so.
     */
    private void loadRelationshipWithoutLayerOrContext() {
        hcin.loadAsserted("""
                <urn:hcin:person:me>      a hcin:Person ; hcin:label "Me" .
                <urn:hcin:person:ninoska> a hcin:Person ; hcin:label "Ninoska" .
                """);
        hcin.load(HcinGraphs.HYPOTHESES, """
                <urn:hcin:relationship:r1> a hcin:Relationship, hcin:SocialRelationship ;
                    hcin:source       <urn:hcin:person:me> ;
                    hcin:target       <urn:hcin:person:ninoska> ;
                    hcin:relationType <http://on7o.io/ontology#invitedBy> .
                """);
        hcin.load(HcinGraphs.PROVENANCE, """
                <urn:hcin:observation:o1> a hcin:Observation ;
                    hcin:about     <urn:hcin:relationship:r1> ;
                    hcin:thoughtId "%s" .
                """.formatted(thoughtId));
    }

    @Test
    void turnsAGapIntoAQuestionNamingTheNodeAndTheProperty() throws IOException {
        loadRelationshipWithoutLayerOrContext();

        NetworkClarificationService.Result result = network.askAboutGaps();

        assertThat(result.asked()).hasSize(2);
        assertThat(result.asked())
                .extracting(ClarificationQuestion::predicateRef)
                .containsExactlyInAnyOrder(
                        "http://on7o.io/hcin#layer", "http://on7o.io/hcin#context");
        assertThat(result.asked())
                .allSatisfy(question ->
                        assertThat(question.subjectRef()).isEqualTo("urn:hcin:relationship:r1"));
    }

    @Test
    void namesThePeopleRatherThanTheUri() throws IOException {
        loadRelationshipWithoutLayerOrContext();

        List<ClarificationQuestion> asked = network.askAboutGaps().asked();

        // A URI is not something to put in front of a person. The relationship
        // has no label of its own, so it is described by the two it connects.
        assertThat(asked).extracting(ClarificationQuestion::text)
                .allMatch(text -> text.contains("invited by relationship between Me and Ninoska"));
    }

    @Test
    void attachesTheQuestionToTheThoughtThatObservedTheNode() throws IOException {
        loadRelationshipWithoutLayerOrContext();

        network.askAboutGaps();

        assertThat(clarification.activeQuestions(thoughtId))
                .hasSize(2)
                .allSatisfy(question -> assertThat(question.required()).isFalse());
    }

    @Test
    void asksTheSameGapOnlyOnce() throws IOException {
        loadRelationshipWithoutLayerOrContext();
        network.askAboutGaps();

        NetworkClarificationService.Result again = network.askAboutGaps();

        // The id comes from the gap, not from the run, so finding it a second
        // time recognizes the question already asked instead of asking afresh.
        assertThat(again.gapsFound()).isEqualTo(2);
        assertThat(again.asked()).isEmpty();
        assertThat(clarification.activeQuestions(thoughtId)).hasSize(2);
    }

    @Test
    void offersTheLayersTheOntologyDeclares() throws IOException {
        loadRelationshipWithoutLayerOrContext();

        List<ClarificationQuestion> asked = network.askAboutGaps().asked();

        // The layers are not a list kept in the question generator. They are the
        // individuals hcin-core.ttl declares, so a layer added to the ontology is
        // offered without anything here changing.
        ClarificationQuestion layer = questionAbout(asked, "http://on7o.io/hcin#layer");
        assertThat(layer.kind()).isEqualTo(AnswerKind.MULTI_CHOICE);
        assertThat(layer.options()).contains(
                "Financial", "Professional", "Family",
                "Intellectual", "Emotional", "Governance");
    }

    @Test
    void offersTheSettingsTheNetworkAlreadyKnows() throws IOException {
        loadRelationshipWithoutLayerOrContext();
        hcin.loadAsserted("""
                <urn:hcin:context:acme> a hcin:Context ; hcin:label "Acme rollout" .
                """);
        hcin.load(HcinGraphs.HYPOTHESES, """
                <urn:hcin:relationship:r0> a hcin:Relationship, hcin:SocialRelationship ;
                    hcin:source  <urn:hcin:person:me> ;
                    hcin:target  <urn:hcin:person:ninoska> ;
                    hcin:layer   hcin:Professional ;
                    hcin:context <urn:hcin:context:acme> .
                """);

        List<ClarificationQuestion> asked = network.askAboutGaps().asked();

        // Which setting a statement belongs to has no enumerable answer set, so
        // what is offered is what the network has already been told. Picking one
        // is what keeps the same setting from entering twice under two spellings,
        // and the box still takes a setting nobody has named yet.
        ClarificationQuestion context = questionAbout(asked, "http://on7o.io/hcin#context");
        assertThat(context.kind()).isEqualTo(AnswerKind.MULTI_SUGGESTION);
        assertThat(context.options()).contains("Acme rollout");
    }

    @Test
    void offersAnOrganizationAsASettingBeforeAnySettingHasBeenNamed() throws IOException {
        loadRelationshipWithoutLayerOrContext();
        hcin.loadAsserted("""
                <urn:hcin:org:expo> a hcin:Organization ; hcin:label "Expo Teleinfo" .
                """);

        List<ClarificationQuestion> asked = network.askAboutGaps().asked();

        // Nothing in this network has ever been given a context, so there is no
        // used value to offer. The ontology says with hcin:suggestFrom which
        // classes hold plausible settings, and an organization is one of them, so
        // the first context question already has something to click.
        assertThat(questionAbout(asked, "http://on7o.io/hcin#context").options())
                .containsExactly("Expo Teleinfo");
    }

    @Test
    void asksAgainWithWhateverTheNetworkLearnedSince() throws IOException {
        loadRelationshipWithoutLayerOrContext();
        network.askAboutGaps();

        hcin.loadAsserted("""
                <urn:hcin:context:acme> a hcin:Context ; hcin:label "Acme rollout" .
                """);
        hcin.load(HcinGraphs.HYPOTHESES, """
                <urn:hcin:relationship:r0> a hcin:Relationship, hcin:SocialRelationship ;
                    hcin:source  <urn:hcin:person:me> ;
                    hcin:target  <urn:hcin:person:ninoska> ;
                    hcin:layer   hcin:Professional ;
                    hcin:context <urn:hcin:context:acme> .
                """);
        network.askAboutGaps();

        // The question was asked before the network had ever heard of that
        // setting. It is the same question, so it keeps its id and its answer,
        // but what it can offer has grown and the open question is asked again
        // with the better offer.
        ClarificationQuestion context = questionAbout(
                clarification.activeQuestions(thoughtId), "http://on7o.io/hcin#context");
        assertThat(context.options()).contains("Acme rollout");
    }

    @Test
    void saysWhereEachGapStandsAsAQuestion() throws IOException {
        loadRelationshipWithoutLayerOrContext();

        // Before anything is asked, an askable gap is waiting rather than absent.
        assertThat(network.stateOf("urn:hcin:relationship:r1", "<http://on7o.io/hcin#layer>"))
                .isEqualTo(GapState.WAITING);

        network.askAboutGaps();

        // Afterwards it is a question with somewhere to go, which is what lets a
        // validation report say which of its lines the reader can act on.
        assertThat(network.stateOf("urn:hcin:relationship:r1", "<http://on7o.io/hcin#layer>"))
                .isEqualTo(GapState.ASKED);
    }

    /** The one question about a given property, which is all these fixtures raise. */
    private static ClarificationQuestion questionAbout(List<ClarificationQuestion> questions,
                                                       String property) {
        return questions.stream()
                .filter(question -> property.equals(question.predicateRef()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no question about " + property));
    }

    @Test
    void raisesNoGapAboutWhichLayerConnectsTwoPlaces() throws IOException {
        hcin.loadAsserted("""
                <urn:hcin:place:venue> a hcin:Entity ; hcin:label "Salon Guarayo" .
                <urn:hcin:place:city>  a hcin:Entity ; hcin:label "Santa Cruz" .
                """);
        hcin.load(HcinGraphs.HYPOTHESES, """
                <urn:hcin:relationship:r2> a hcin:Relationship ;
                    hcin:source       <urn:hcin:place:venue> ;
                    hcin:target       <urn:hcin:place:city> ;
                    hcin:relationType <http://on7o.io/ontology#locatedIn> .
                """);
        hcin.load(HcinGraphs.PROVENANCE, """
                <urn:hcin:observation:o2> a hcin:Observation ;
                    hcin:about     <urn:hcin:relationship:r2> ;
                    hcin:thoughtId "%s" .
                """.formatted(thoughtId));

        NetworkClarificationService.Result result = network.askAboutGaps();

        // The shapes ask about a layer and a setting only of edges between people
        // and organizations, and this edge joins a venue to its city. There used
        // to be a filter here throwing the finding away after the fact; the model
        // now says it, so the finding is never raised.
        assertThat(result.gapsFound()).isZero();
        assertThat(result.asked()).isEmpty();
    }

    @Test
    void reportsAGapNoThoughtIsRecordedAsHavingObserved() throws IOException {
        hcin.loadAsserted("""
                <urn:hcin:person:me>      a hcin:Person ; hcin:label "Me" .
                <urn:hcin:person:ninoska> a hcin:Person ; hcin:label "Ninoska" .
                """);
        hcin.load(HcinGraphs.HYPOTHESES, """
                <urn:hcin:relationship:orphan> a hcin:Relationship, hcin:SocialRelationship ;
                    hcin:source <urn:hcin:person:me> ;
                    hcin:target <urn:hcin:person:ninoska> .
                """);

        NetworkClarificationService.Result result = network.askAboutGaps();

        // A question has to be asked of someone, in some conversation. A node
        // with no thought behind it is reported rather than guessed at.
        assertThat(result.asked()).isEmpty();
        assertThat(result.unattributed()).containsExactly("urn:hcin:relationship:orphan");
    }
}
