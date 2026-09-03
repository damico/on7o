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
 * The answers to the network's own questions becoming part of the network.
 *
 * <p>What is under test is the half that was missing: a gap became a question and
 * the question was answered, and unless the answer is written back the graph goes
 * on missing exactly what the user just said.
 */
class NetworkAnswerServiceTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-24T15:30:00Z");
    private static final String LAYER = "http://on7o.io/hcin#layer";
    private static final String CONTEXT = "http://on7o.io/hcin#context";

    private HcinFixture hcin;
    private ClarificationService clarification;
    private NetworkClarificationService network;
    private NetworkAnswerService answers;
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
        answers = new NetworkAnswerService(hcin.repository(), clarification);

        thoughtId = new ThoughtService(store)
                .ingestText("A Ninoska me convidou.", CAPTURED_AT, Thought.SOURCE_SYNTHETIC, null)
                .id();

        hcin.loadAsserted("""
                <urn:hcin:person:me>      a hcin:Person ; hcin:label "Me" .
                <urn:hcin:person:ninoska> a hcin:Person ; hcin:label "Ninoska" .
                <urn:hcin:org:expo>       a hcin:Organization ; hcin:label "Expo Teleinfo" .
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

    /** Answers the question the network asked about one property. */
    private void answer(String property, String text) throws IOException {
        network.askAboutGaps();
        String questionId = clarification.activeQuestions(thoughtId).stream()
                .filter(question -> property.equals(question.predicateRef()))
                .findFirst()
                .orElseThrow()
                .id();
        clarification.submitAnswers(thoughtId,
                List.of(new AnswerSubmission(questionId, text, false)));
    }

    @Test
    void writesTheLayerTheUserNamedOntoTheRelationship() throws IOException {
        answer(LAYER, "Professional");

        assertThat(answers.apply(thoughtId)).isEqualTo(1);
        assertThat(hcin.repository().export(HcinGraphs.HYPOTHESES))
                .contains("hcin:layer")
                .contains("hcin:Professional");
    }

    @Test
    void writesEveryLayerOfATieThatLivesInMoreThanOne() throws IOException {
        answer(LAYER, "Professional, Financial");

        // A tie can be professional and financial at once, and the answer says so
        // as a list. Keeping only the first would quietly discard half of it.
        assertThat(answers.apply(thoughtId)).isEqualTo(2);
        assertThat(hcin.repository().export(HcinGraphs.HYPOTHESES))
                .contains("hcin:Professional")
                .contains("hcin:Financial");
    }

    @Test
    void writesTheAnswerIntoTheGraphTheStatementLivesIn() throws IOException {
        answer(LAYER, "Professional");
        answers.apply(thoughtId);

        // The relationship is a hypothesis. A layer written into the asserted
        // graph would leave the hypothesis still incomplete where the shapes read
        // it, and the gap would be reported forever.
        assertThat(hcin.repository().export(HcinGraphs.ASSERTED)).doesNotContain("hcin:layer");
        assertThat(hcin.repository().export(HcinGraphs.HYPOTHESES)).contains("hcin:layer");
    }

    @Test
    void closesTheGapThatRaisedTheQuestion() throws IOException {
        answer(LAYER, "Professional");
        answers.apply(thoughtId);

        // The whole point. What the shapes reported as missing is no longer
        // missing, so the network stops asking.
        assertThat(network.askAboutGaps().gapsFound()).isEqualTo(1);
    }

    @Test
    void pointsASettingAtTheNodeTheNetworkAlreadyHas() throws IOException {
        answer(CONTEXT, "Expo Teleinfo");
        answers.apply(thoughtId);

        // Picking what was offered has to land on the organization that was
        // offered. Minting a second node with the same name would split the
        // network in two while looking like it had answered the question.
        assertThat(hcin.repository().export(HcinGraphs.HYPOTHESES))
                .contains("hcin:context")
                .contains("org:expo");
    }

    @Test
    void mintsASettingNobodyHadNamedYet() throws IOException {
        answer(CONTEXT, "Kaizen negotiation");
        answers.apply(thoughtId);

        // The user was told they could name a setting the network had never heard
        // of. Refusing it afterwards would have made that offer a lie.
        String hypotheses = hcin.repository().export(HcinGraphs.HYPOTHESES);
        assertThat(hypotheses).contains("kaizen-negotiation");
        assertThat(hypotheses).contains("Kaizen negotiation");
    }

    @Test
    void leavesAnAnswerThatIsNotAValueAlone() throws IOException {
        answer(LAYER, "whatever you think");

        // Nothing in the ontology is called that. The answer stays on record and
        // the gap stays open, which is honest; inventing a layer to satisfy the
        // shape would not be.
        assertThat(answers.apply(thoughtId)).isZero();
        assertThat(hcin.repository().export(HcinGraphs.HYPOTHESES)).doesNotContain("hcin:layer");
    }

    @Test
    void appliesTheSameAnswerTwiceWithoutSayingItTwice() throws IOException {
        answer(LAYER, "Professional");
        answers.apply(thoughtId);
        long size = hcin.repository().size(HcinGraphs.HYPOTHESES);

        answers.apply(thoughtId);

        assertThat(hcin.repository().size(HcinGraphs.HYPOTHESES)).isEqualTo(size);
    }
}
