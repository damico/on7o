package org.on7o.server.analysis;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The clarification pipeline as an API client sees it: text in, questions out,
 * answers back, knowledge artifact at the end.
 *
 * <p>The model is stubbed rather than called, so the test asserts on the
 * pipeline's own behaviour: idempotency, question identity, what blocks
 * consolidation and what does not.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisFlowTest {

    private static final String RTHOUGHT = """
            @prefix on7o: <http://on7o.io/ontology#> .
            on7o:bob on7o:lunchedWith on7o:me .
            """;

    private static final String QTHOUGHT = """
            @prefix on7o: <http://on7o.io/ontology#> .
            on7o:q1 a on7o:ClarificationQuestion .
            """;

    /**
     * Five triples: two individuals typed in the world, one class declaration
     * about the vocabulary, one link between resources and one literal.
     */
    private static final String CTHOUGHT = """
            @prefix on7o: <http://on7o.io/ontology#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            on7o:Person a owl:Class .
            on7o:bob a on7o:Person ;
                     on7o:worksAt on7o:acme ;
                     on7o:confidence 0.9 .
            on7o:acme a on7o:Organization .
            """;

    private static final List<String> QUESTIONS = List.of(
            "Bob pertence a ACME?", "Qual contrato foi discutido?");

    private static final Path ROOT = temporaryRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("on7o.storage.root", ROOT::toString);
        registry.add("on7o.stt.enabled", () -> false);
        registry.add("on7o.hcin.location", () -> "mem");
    }

    private static Path temporaryRoot() {
        try {
            return Files.createTempDirectory("on7o-analysis");
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

    @BeforeEach
    void stubTheModel() {
        reset(interpreter);
        when(interpreter.interpretRaw(anyString())).thenReturn(RTHOUGHT);
        when(interpreter.questionRaw(anyString())).thenReturn(new QThoughtResult(QUESTIONS, QTHOUGHT));
        when(interpreter.consolidate(anyString(), anyString(), any(), any())).thenReturn(CTHOUGHT);
        when(interpreter.scanEntities(anyString())).thenReturn(List.of());
    }

    @Test
    void analyzesAThoughtIntoQuestionsWithIds() throws Exception {
        String id = givenAThought("Almocei com o Bob. Discutimos o contrato da ACME.");

        mvc.perform(post("/api/thoughts/{id}/analyze", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thoughtId").value(id))
                .andExpect(jsonPath("$.analysisStatus").value("QUESTIONS_REQUIRED"))
                .andExpect(jsonPath("$.reused").value(false))
                .andExpect(jsonPath("$.semanticArtifact").value("rthought.ttl"))
                .andExpect(jsonPath("$.questionsArtifact").value("qthought.ttl"))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].id").isNotEmpty())
                .andExpect(jsonPath("$.questions[0].thoughtId").value(id))
                .andExpect(jsonPath("$.questions[0].text").value(QUESTIONS.get(0)))
                .andExpect(jsonPath("$.questions[0].status").value("OPEN"))
                .andExpect(jsonPath("$.questions[0].required").value(true));
    }

    @Test
    void returnsAStoredAnalysisWithoutCallingTheModelAgain() throws Exception {
        String id = givenAThought("Almocei com o Bob.");

        List<String> first = analyze(id, null);
        verify(interpreter, times(1)).interpretRaw(anyString());

        mvc.perform(post("/api/thoughts/{id}/analyze", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reused").value(true));

        verify(interpreter, times(1)).interpretRaw(anyString());
        verify(interpreter, times(1)).questionRaw(anyString());
        assertThat(analyze(id, null)).isEqualTo(first);
    }

    @Test
    void reanalyzingRetiresTheOldQuestionsInsteadOfLosingThem() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        List<String> first = analyze(id, null);

        List<String> second = analyze(id, "{\"force\":true}");

        verify(interpreter, times(2)).interpretRaw(anyString());
        assertThat(second).doesNotContainAnyElementsOf(first);

        JsonNode questions = readTree(mvc.perform(get("/api/thoughts/{id}/questions", id))
                .andExpect(status().isOk())
                .andReturn()).get("questions");

        assertThat(questions).hasSize(4);
        assertThat(statusesOf(questions, first)).containsOnly("OBSOLETE");
        assertThat(statusesOf(questions, second)).containsOnly("OPEN");
    }

    @Test
    void acceptsAnswersByIdInAnyOrderAndReportsTheNewStatuses() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        List<String> questionIds = analyze(id, null);

        mvc.perform(post("/api/thoughts/{id}/answers", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionId":"%s","answer":"O contrato de renovacao."},
                                  {"questionId":"%s","answer":"Sim, trabalha na ACME."}
                                ]}
                                """.formatted(questionIds.get(1), questionIds.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].status").value("ANSWERED"))
                .andExpect(jsonPath("$.questions[1].status").value("ANSWERED"));

        mvc.perform(get("/api/thoughts/{id}/answers", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.length()").value(2))
                .andExpect(jsonPath("$.history.length()").value(0));
    }

    @Test
    void keepsSupersededAnswersInTheHistory() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        String questionId = analyze(id, null).get(0);

        answer(id, questionId, "Talvez.");
        answer(id, questionId, "Sim.");

        mvc.perform(get("/api/thoughts/{id}/answers", id).param("history", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.length()").value(1))
                .andExpect(jsonPath("$.answers[0].answer").value("Sim."))
                .andExpect(jsonPath("$.answers[0].revision").value(2))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].answer").value("Talvez."));
    }

    @Test
    void refusesAnAnswerToAQuestionThatWasNeverAsked() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        analyze(id, null);

        mvc.perform(post("/api/thoughts/{id}/answers", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":\"q-nope\",\"answer\":\"Sim.\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("q-nope")));
    }

    @Test
    void willNotConsolidateWhileRequiredQuestionsAreUnanswered() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        List<String> questionIds = analyze(id, null);
        answer(id, questionIds.get(0), "Sim.");

        mvc.perform(post("/api/thoughts/{id}/consolidate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISSING_REQUIRED_ANSWERS"))
                .andExpect(jsonPath("$.artifact").doesNotExist())
                .andExpect(jsonPath("$.openRequiredIds[0]").value(questionIds.get(1)));

        verify(interpreter, never()).consolidate(anyString(), anyString(), any(), any());
    }

    @Test
    void consolidatesOnceEverythingIsAnsweredAndReportsWhatTheArtifactHolds() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        List<String> questionIds = analyze(id, null);
        answer(id, questionIds.get(0), "Sim, trabalha na ACME.");
        answer(id, questionIds.get(1), "O contrato de renovacao.");

        mvc.perform(post("/api/thoughts/{id}/consolidate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSOLIDATED"))
                .andExpect(jsonPath("$.reused").value(false))
                .andExpect(jsonPath("$.artifact").value("cthought.ttl"))
                .andExpect(jsonPath("$.statements").value(5))
                .andExpect(jsonPath("$.entities").value(2))
                .andExpect(jsonPath("$.relationships").value(1))
                .andExpect(jsonPath("$.openRequiredIds.length()").value(0));

        mvc.perform(post("/api/thoughts/{id}/consolidate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSOLIDATED"))
                .andExpect(jsonPath("$.reused").value(true))
                .andExpect(jsonPath("$.statements").value(5));

        verify(interpreter, times(1)).consolidate(anyString(), anyString(), any(), any());
    }

    @Test
    void consolidatesWithoutTheMissingAnswersWhenAskedTo() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        analyze(id, null);

        mvc.perform(post("/api/thoughts/{id}/consolidate", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowIncomplete\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSOLIDATED"));
    }

    @Test
    void keepsQuestionsQueryableAfterConsolidation() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        List<String> questionIds = analyze(id, null);
        answer(id, questionIds.get(0), "Sim.");
        answer(id, questionIds.get(1), "O contrato.");

        mvc.perform(post("/api/thoughts/{id}/consolidate", id)).andExpect(status().isOk());

        mvc.perform(get("/api/thoughts/{id}/questions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].status").value("ANSWERED"));
    }

    @Test
    void answersNothingAboutAThoughtThatDoesNotExist() throws Exception {
        mvc.perform(post("/api/thoughts/{id}/analyze", "20260101T000000Z-deadbeef"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/thoughts/{id}/questions", "20260101T000000Z-deadbeef"))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesToAnalyzeAThoughtWithNothingToAnalyze() throws Exception {
        String id = givenAThought("Almocei com o Bob.");
        Files.delete(ROOT.resolve(id).resolve("transcription.json"));

        mvc.perform(post("/api/thoughts/{id}/analyze", id))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String givenAThought(String text) throws Exception {
        MvcResult result = mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"%s","capturedAt":"2026-08-24T12:30:00-03:00"}
                                """.formatted(text)))
                .andExpect(status().isCreated())
                .andReturn();

        return readTree(result).get("thoughtId").asText();
    }

    /** Analyzes a thought and returns the ids of the questions it is now asking. */
    private List<String> analyze(String id, String body) throws Exception {
        var request = post("/api/thoughts/{id}/analyze", id);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        JsonNode questions = readTree(mvc.perform(request).andExpect(status().isOk()).andReturn())
                .get("questions");

        return questions.findValues("id").stream().map(JsonNode::asText).toList();
    }

    private void answer(String thoughtId, String questionId, String answer) throws Exception {
        mvc.perform(post("/api/thoughts/{id}/answers", thoughtId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"%s","answer":"%s"}]}
                                """.formatted(questionId, answer)))
                .andExpect(status().isOk());
    }

    private List<String> statusesOf(JsonNode questions, List<String> ids) {
        List<String> statuses = new java.util.ArrayList<>();
        questions.forEach(question -> {
            if (ids.contains(question.get("id").asText())) {
                statuses.add(question.get("status").asText());
            }
        });
        return statuses;
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
