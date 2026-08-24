package org.on7o.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.on7o.server.ingest.Thought;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of {@code POST /api/thoughts/text}: the happy path, the
 * validation rules that keep synthetic fixtures unambiguous, and the fact that
 * a text thought is served by the endpoints that already existed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TextThoughtControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper mapper;

    /** One store per test class run, so the tests never touch the real data directory. */
    private static final Path ROOT = temporaryRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("on7o.storage.root", ROOT::toString);
        registry.add("on7o.stt.enabled", () -> false);
        registry.add("on7o.hcin.location", () -> "mem");
    }

    private static Path temporaryRoot() {
        try {
            return Files.createTempDirectory("on7o-text-thoughts");
        } catch (Exception e) {
            throw new IllegalStateException("could not create a temporary store", e);
        }
    }

    @Test
    void createsThoughtFromText() throws Exception {
        mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Almocei com o Bob hoje. Discutimos a renovacao do contrato da ACME.",
                                  "capturedAt": "2026-08-24T12:30:00-03:00",
                                  "source": "synthetic"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.thoughtId").isNotEmpty())
                .andExpect(jsonPath("$.source").value(Thought.SOURCE_SYNTHETIC))
                .andExpect(jsonPath("$.capturedAt").value("2026-08-24T12:30:00-03:00"))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty())
                .andExpect(jsonPath("$.text").value(
                        "Almocei com o Bob hoje. Discutimos a renovacao do contrato da ACME."));
    }

    @Test
    void defaultsSourceToSynthetic() throws Exception {
        mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Sem origem declarada.", "capturedAt": "2026-08-24T15:30:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value(Thought.SOURCE_SYNTHETIC));
    }

    @Test
    void servesTheTextThoughtThroughTheExistingEndpoints() throws Exception {
        String id = createThought("Paguei a mensalidade da ABC.", "2026-08-24T12:30:00-03:00");

        mvc.perform(get("/api/thoughts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.source").value(Thought.SOURCE_SYNTHETIC))
                .andExpect(jsonPath("$.audioFile").doesNotExist())
                .andExpect(jsonPath("$.capturedAt").value("2026-08-24T15:30:00Z"));

        mvc.perform(get("/api/thoughts/{id}/transcription", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Paguei a mensalidade da ABC."));

        mvc.perform(get("/api/thoughts/{id}/audio", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void preservesTheInstantBehindTheOffset() throws Exception {
        String withOffset = createThought("Com offset.", "2026-08-24T12:30:00-03:00");
        String inUtc = createThought("Em UTC.", "2026-08-24T15:30:00Z");

        assertThat(capturedAtOf(withOffset)).isEqualTo(capturedAtOf(inUtc));
    }

    @Test
    void rejectsMissingCapturedAt() throws Exception {
        mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Sem data."}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capturedAt").isNotEmpty());
    }

    @Test
    void rejectsBlankText() throws Exception {
        mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "   ", "capturedAt": "2026-08-24T15:30:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").isNotEmpty());
    }

    @Test
    void rejectsMalformedCapturedAt() throws Exception {
        mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Data invalida.", "capturedAt": "24/08/2026 12:30"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("capturedAt")));
    }

    @Test
    void rejectsTimestampWithoutAnOffset() throws Exception {
        mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Sem fuso.", "capturedAt": "2026-08-24T12:30:00"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String createThought(String text, String capturedAt) throws Exception {
        MvcResult result = mvc.perform(post("/api/thoughts/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new TextThoughtRequest(text,
                                        java.time.OffsetDateTime.parse(capturedAt), null))))
                .andExpect(status().isCreated())
                .andReturn();

        return read(result).get("thoughtId").asText();
    }

    private String capturedAtOf(String id) throws Exception {
        MvcResult result = mvc.perform(get("/api/thoughts/{id}", id))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("capturedAt").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
