package org.on7o.server.hcinfin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.on7o.server.llm.QThoughtResult;
import org.on7o.server.llm.ThoughtInterpreter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole HCIN financial pipeline, end to end, over the API.
 *
 * <pre>
 * text in -> analyze -> questions -> answers -> consolidate -> reconcile
 *         -> validate -> projection
 * </pre>
 *
 * <p>No network and no API key. The model is replaced by a deterministic
 * interpreter driven by the synthetic dataset: it asks the questions the fixture
 * scripts and returns the consolidation the fixture holds. What is under test is
 * therefore the pipeline, not the model.
 *
 * <p>Every assertion is made against a fixed instant, so this test says the same
 * thing whenever it runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FinancialProjectionEndToEndTest {

    private static final Path ROOT = temporaryRoot();

    @DynamicPropertySource
    static void isolatedStores(DynamicPropertyRegistry registry) {
        registry.add("on7o.storage.root", ROOT::toString);
        registry.add("on7o.stt.enabled", () -> false);
        registry.add("on7o.hcin.location", () -> "mem");
    }

    private static Path temporaryRoot() {
        try {
            return Files.createTempDirectory("on7o-hcin-fin");
        } catch (Exception e) {
            throw new IllegalStateException("could not create a temporary store", e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private ThoughtInterpreter interpreter;

    @Autowired
    private org.on7o.server.hcin.HcinRepository hcin;

    private SyntheticDataset dataset;
    private JsonNode expected;

    @BeforeEach
    void scriptTheModel() throws Exception {
        dataset = new SyntheticDataset();
        expected = dataset.expected();

        // The dataset outlives a single test method, so each one starts from an
        // empty network. Otherwise the second test would find the first test's
        // lunches still on record.
        org.on7o.server.hcin.HcinGraphs.all().stream()
                .filter(graph -> !graph.equals(org.on7o.server.hcin.HcinGraphs.SCHEMA))
                .forEach(hcin::clear);

        // The interpreter sees a transcription and nothing else, exactly as the
        // real one does. Which transcript it belongs to is worked out from the
        // text, so nothing here depends on ids the pipeline never passes along.
        when(interpreter.interpretRaw(anyString())).thenAnswer(call -> {
            String text = call.getArgument(0);
            return "@prefix on7o: <http://on7o.io/ontology#> .\n# rThought for: " + text + "\n";
        });

        when(interpreter.questionRaw(anyString())).thenAnswer(call -> {
            String rThought = call.getArgument(0);
            String id = dataset.transcriptForText(textOf(rThought)).id();
            return new QThoughtResult(dataset.questionsFor(id),
                    "@prefix on7o: <http://on7o.io/ontology#> .\n# qThought\n");
        });

        when(interpreter.consolidate(anyString(), anyString(), any(), any())).thenAnswer(call -> {
            String rThought = call.getArgument(0);
            return dataset.consolidationForText(textOf(rThought));
        });

        when(interpreter.scanEntities(anyString())).thenReturn(List.of());
    }

    /** Recovers the transcription from the rThought the stub wrote it into. */
    private static String textOf(String rThought) {
        int marker = rThought.indexOf("# rThought for: ");
        return rThought.substring(marker + "# rThought for: ".length()).trim();
    }

    @Test
    void runsEveryTranscriptThroughToAProjection() throws Exception {
        Map<String, String> thoughtIds = ingestAndReconcileEverything();

        assertThat(thoughtIds).hasSize(dataset.transcripts().size());

        JsonNode projection = projectionAt(expected.get("asOf").asText());

        assertNodes(projection);
        assertEdges(projection);
        assertGroups(projection);
        assertOrderings(projection);
    }

    @Test
    void asksTheUserAndRecordsWhatTheySaid() throws Exception {
        String thoughtId = ingest("transcript-003");
        analyze(thoughtId);

        JsonNode questions = questionsOf(thoughtId);
        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).get("thoughtId").asText()).isEqualTo(thoughtId);

        answer(thoughtId, "transcript-003");

        JsonNode answers = read(mvc.perform(get("/api/thoughts/{id}/answers", thoughtId))
                .andExpect(status().isOk()).andReturn()).get("answers");

        // Three questions were asked; the user answered two and declined one.
        assertThat(answers).hasSize(3);
        assertThat(statusesOf(questionsOf(thoughtId)))
                .containsExactlyInAnyOrder("ANSWERED", "SKIPPED", "ANSWERED");
    }

    @Test
    void leavesTheUnansweredQuestionVisibleInTheShapes() throws Exception {
        ingestAndReconcileEverything();

        JsonNode report = read(mvc.perform(get("/api/hcin/validate").param("graph", "hypotheses"))
                .andExpect(status().isOk()).andReturn());

        String expectedMessage = expected.get("clarification").get("expectedShaclMessage").asText();
        List<String> messages = new ArrayList<>();
        report.get("findings").forEach(finding -> messages.add(finding.get("message").asText()));

        assertThat(messages).anyMatch(message -> message.contains(expectedMessage));
        assertThat(report.get("findings")).anySatisfy(finding ->
                assertThat(finding.get("severity").asText()).isEqualTo("CLARIFICATION_CANDIDATE"));
    }

    @Test
    void keepsOneApprovalFromBecomingStandingPower() throws Exception {
        ingestAndReconcileEverything();

        // Maria's authority was stated with no scope, so it is held as a
        // hypothesis however confidently the thought asserted it.
        String hypotheses = mvc.perform(get("/api/hcin/data").param("graph", "hypotheses"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(hypotheses).contains("FinancialAuthority").contains("maria");
        assertThat(hypotheses).doesNotContain("hcin:Asserted");
    }

    @Test
    void showsTheNetworkAsItStoodSixMonthsEarlier() throws Exception {
        ingestAndReconcileEverything();

        JsonNode historical = expected.get("historical");
        JsonNode past = projectionAt(historical.get("asOf").asText());

        Map<String, JsonNode> nodes = nodesById(past);

        historical.get("presentNodes").forEach(id -> assertThat(nodes).containsKey(id.asText()));
        historical.get("absentNodes").forEach(id -> assertThat(nodes).doesNotContainKey(id.asText()));

        historical.get("authority").fields().forEachRemaining(entry -> {
            JsonNode node = nodes.get(entry.getKey());
            assertThat(node).as("node %s", entry.getKey()).isNotNull();
            assertThat(node.get("financialAuthority").asText()).isEqualTo(entry.getValue().asText());
        });

        // Nothing had been said or paid yet at that point.
        nodes.values().forEach(node -> {
            if (!"EGO".equals(node.get("type").asText())) {
                assertThat(node.get("interactionProximity").asDouble()).isZero();
                assertThat(new BigDecimal(node.get("financialMagnitude").asText())).isZero();
            }
        });
    }

    @Test
    void producesTheSameProjectionWhenEverythingIsReconciledTwice() throws Exception {
        Map<String, String> thoughtIds = ingestAndReconcileEverything();
        String before = projectionJsonAt(expected.get("asOf").asText());

        for (String thoughtId : thoughtIds.values()) {
            JsonNode again = read(mvc.perform(post("/api/thoughts/{id}/reconcile", thoughtId))
                    .andExpect(status().isOk()).andReturn());
            assertThat(again.get("entitiesCreated").asInt()).isZero();
            assertThat(again.get("statementsAsserted").asLong()).isZero();
        }

        assertThat(projectionJsonAt(expected.get("asOf").asText())).isEqualTo(before);
    }

    @Test
    void explainsEveryDistanceItDraws() throws Exception {
        ingestAndReconcileEverything();

        JsonNode metrics = read(mvc.perform(get("/api/hcin/financial-projection/metrics")
                        .param("asOf", expected.get("asOf").asText()))
                .andExpect(status().isOk()).andReturn());

        JsonNode bob = null;
        for (JsonNode entry : metrics.get("metrics")) {
            if ("urn:hcin:person:bob".equals(entry.get("entityUri").asText())) {
                bob = entry;
            }
        }

        assertThat(bob).isNotNull();
        JsonNode contributions = bob.get("proximity").get("contributions");
        assertThat(contributions).hasSize(3);
        assertThat(contributions.get(0).get("type").asText()).isEqualTo("meeting");
        assertThat(contributions.get(0).get("weight").asDouble()).isEqualTo(1.0);
        assertThat(contributions.get(0).get("decay").asDouble()).isEqualTo(1.0);
        assertThat(bob.get("vector").get("dependency").isNull()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Driving the pipeline
    // -------------------------------------------------------------------------

    /** Every transcript, all the way from text to merged knowledge. */
    private Map<String, String> ingestAndReconcileEverything() throws Exception {
        Map<String, String> thoughtIds = new HashMap<>();

        for (SyntheticDataset.Transcript transcript : dataset.transcripts()) {
            String thoughtId = ingest(transcript.id());
            thoughtIds.put(transcript.id(), thoughtId);

            analyze(thoughtId);
            answer(thoughtId, transcript.id());
            consolidate(thoughtId);
            reconcile(thoughtId);
        }

        return thoughtIds;
    }

    private String ingest(String transcriptId) throws Exception {
        SyntheticDataset.Transcript transcript = dataset.transcripts().stream()
                .filter(candidate -> candidate.id().equals(transcriptId))
                .findFirst()
                .orElseThrow();

        MvcResult result = mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "text", transcript.text(),
                                "capturedAt", transcript.timestamp(),
                                "source", "synthetic"))))
                .andExpect(status().isCreated())
                .andReturn();

        return read(result).get("thoughtId").asText();
    }

    private void analyze(String thoughtId) throws Exception {
        mvc.perform(post("/api/thoughts/{id}/analyze", thoughtId)).andExpect(status().isOk());
    }

    /**
     * Answers by question id, looked up from the text the fixture scripts.
     *
     * <p>Never by position: the ids are what the API is built on, and a fixture
     * that relied on order would be testing something the contract does not
     * promise.
     */
    private void answer(String thoughtId, String transcriptId) throws Exception {
        Map<String, String> idsByQuestion = new HashMap<>();
        questionsOf(thoughtId).forEach(question ->
                idsByQuestion.put(question.get("text").asText(), question.get("id").asText()));

        List<Map<String, Object>> answers = new ArrayList<>();
        for (SyntheticDataset.ScriptedAnswer scripted : dataset.answersFor(transcriptId)) {
            String questionId = idsByQuestion.get(scripted.question());
            assertThat(questionId).as("question %s was never asked", scripted.question()).isNotNull();

            Map<String, Object> answer = new HashMap<>();
            answer.put("questionId", questionId);
            answer.put("skipped", scripted.isSkipped());
            if (!scripted.isSkipped()) {
                answer.put("answer", scripted.answer());
            }
            answers.add(answer);
        }

        mvc.perform(post("/api/thoughts/{id}/answers", thoughtId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("answers", answers))))
                .andExpect(status().isOk());
    }

    private void consolidate(String thoughtId) throws Exception {
        JsonNode result = read(mvc.perform(post("/api/thoughts/{id}/consolidate", thoughtId))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("status").asText()).isEqualTo("CONSOLIDATED");
    }

    private void reconcile(String thoughtId) throws Exception {
        JsonNode result = read(mvc.perform(post("/api/thoughts/{id}/reconcile", thoughtId))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("status").asText()).isEqualTo("RECONCILED");
    }

    // -------------------------------------------------------------------------
    // Checking the projection against the fixture
    // -------------------------------------------------------------------------

    private void assertNodes(JsonNode projection) {
        Map<String, JsonNode> nodes = nodesById(projection);

        assertThat(nodes).containsKey(expected.get("ego").asText());
        assertThat(nodes.get(expected.get("ego").asText()).get("type").asText()).isEqualTo("EGO");

        for (JsonNode want : expected.get("nodes")) {
            String id = want.get("id").asText();
            JsonNode got = nodes.get(id);
            assertThat(got).as("node %s", id).isNotNull();

            assertThat(got.get("label").asText()).isEqualTo(want.get("label").asText());
            assertThat(got.get("type").asText()).isEqualTo(want.get("type").asText());
            assertThat(got.get("financialAuthority").asText())
                    .as("authority of %s", id)
                    .isEqualTo(want.get("financialAuthority").asText());
            assertThat(new BigDecimal(got.get("financialMagnitude").asText()))
                    .as("magnitude of %s", id)
                    .isEqualByComparingTo(new BigDecimal(want.get("financialMagnitude").asText()));

            if (want.has("interactions")) {
                assertThat(got.get("interactions").asInt())
                        .as("interactions of %s", id)
                        .isEqualTo(want.get("interactions").asInt());
            }
            if (want.has("organizationIds")) {
                List<String> organizations = new ArrayList<>();
                got.get("organizationIds").forEach(node -> organizations.add(node.asText()));
                List<String> wanted = new ArrayList<>();
                want.get("organizationIds").forEach(node -> wanted.add(node.asText()));
                assertThat(organizations).as("memberships of %s", id)
                        .containsExactlyInAnyOrderElementsOf(wanted);
            }
        }
    }

    private void assertEdges(JsonNode projection) {
        Map<String, JsonNode> edges = new HashMap<>();
        projection.get("edges").forEach(edge -> edges.put(edge.get("target").asText(), edge));

        for (JsonNode want : expected.get("edges")) {
            String target = want.get("target").asText();
            JsonNode got = edges.get(target);
            assertThat(got).as("edge to %s", target).isNotNull();

            assertThat(got.get("source").asText()).isEqualTo(expected.get("ego").asText());
            assertThat(got.get("directFinancialFlow").asBoolean())
                    .as("direct flow to %s", target)
                    .isEqualTo(want.get("directFinancialFlow").asBoolean());
            assertThat(got.get("flowDirection").asText()).isEqualTo(want.get("flowDirection").asText());
            assertThat(got.get("strokeStyle").asText()).isEqualTo(want.get("strokeStyle").asText());
        }
    }

    private void assertGroups(JsonNode projection) {
        Map<String, List<String>> groups = new HashMap<>();
        projection.get("groups").forEach(group -> {
            List<String> members = new ArrayList<>();
            group.get("memberNodeIds").forEach(member -> members.add(member.asText()));
            groups.put(group.get("id").asText(), members);
        });

        for (JsonNode want : expected.get("groups")) {
            String id = want.get("id").asText();
            List<String> wanted = new ArrayList<>();
            want.get("memberNodeIds").forEach(member -> wanted.add(member.asText()));

            assertThat(groups.get(id)).as("members of %s", id)
                    .containsExactlyInAnyOrderElementsOf(wanted);
        }
    }

    /**
     * The orderings, which are the real invariants: absolute distances and radii
     * depend on parameters that are meant to be tuned, but who is nearer and who
     * is bigger must survive the tuning.
     */
    private void assertOrderings(JsonNode projection) {
        Map<String, JsonNode> nodes = nodesById(projection);

        for (JsonNode pair : expected.get("closerThan")) {
            String near = pair.get(0).asText();
            String far = pair.get(1).asText();
            assertThat(nodes.get(near).get("visualDistance").asDouble())
                    .as("%s should be drawn closer than %s", near, far)
                    .isLessThan(nodes.get(far).get("visualDistance").asDouble());
        }

        for (JsonNode pair : expected.get("largerThan")) {
            String bigger = pair.get(0).asText();
            String smaller = pair.get(1).asText();
            assertThat(nodes.get(bigger).get("radius").asDouble())
                    .as("%s should be drawn bigger than %s", bigger, smaller)
                    .isGreaterThan(nodes.get(smaller).get("radius").asDouble());
        }
    }

    // -------------------------------------------------------------------------
    // Reading responses
    // -------------------------------------------------------------------------

    private JsonNode projectionAt(String asOf) throws Exception {
        return mapper.readTree(projectionJsonAt(asOf));
    }

    private String projectionJsonAt(String asOf) throws Exception {
        return mvc.perform(get("/api/hcin/financial-projection").param("asOf", asOf))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode questionsOf(String thoughtId) throws Exception {
        return read(mvc.perform(get("/api/thoughts/{id}/questions", thoughtId))
                .andExpect(status().isOk()).andReturn()).get("questions");
    }

    private List<String> statusesOf(JsonNode questions) {
        List<String> statuses = new ArrayList<>();
        questions.forEach(question -> statuses.add(question.get("status").asText()));
        return statuses;
    }

    private Map<String, JsonNode> nodesById(JsonNode projection) {
        Map<String, JsonNode> nodes = new HashMap<>();
        projection.get("nodes").forEach(node -> nodes.put(node.get("id").asText(), node));
        return nodes;
    }

    private JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
