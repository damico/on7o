package org.on7o.server.hcinfin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The synthetic HCIN dataset, loaded from {@code src/test/resources/hcin-fin}.
 *
 * <p>Four artifacts, each with one job:
 * <ul>
 *   <li>{@code synthetic-transcripts.jsonl} - what was said, and when;</li>
 *   <li>{@code synthetic-answers.json} - the clarification script, in order, a
 *       null answer meaning the user was asked and declined;</li>
 *   <li>{@code cthoughts/*.ttl} - what a model would have consolidated from each
 *       transcript, which is what lets the pipeline run with no model at all;</li>
 *   <li>{@code expected-projection.json} - what the whole thing must produce.</li>
 * </ul>
 *
 * <p>The answers file doubles as the question script: the stub asks exactly the
 * questions it holds, in the order it holds them. One file, so a question and
 * its answer can never drift apart.
 */
public final class SyntheticDataset {

    private static final String BASE = "hcin-fin/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Transcript> transcripts = new ArrayList<>();
    private final Map<String, List<ScriptedAnswer>> script = new LinkedHashMap<>();
    private final Map<String, String> consolidations = new LinkedHashMap<>();
    private final JsonNode expected;

    public SyntheticDataset() throws IOException {
        for (String line : read("synthetic-transcripts.jsonl").split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = MAPPER.readTree(line);
            transcripts.add(new Transcript(
                    node.get("id").asText(),
                    node.get("timestamp").asText(),
                    node.get("text").asText()));
        }

        JsonNode answers = MAPPER.readTree(read("synthetic-answers.json"));
        answers.fieldNames().forEachRemaining(id -> {
            List<ScriptedAnswer> scripted = new ArrayList<>();
            answers.get(id).forEach(entry -> scripted.add(new ScriptedAnswer(
                    entry.get("question").asText(),
                    entry.get("answer").isNull() ? null : entry.get("answer").asText())));
            script.put(id, scripted);
        });

        for (Transcript transcript : transcripts) {
            consolidations.put(transcript.text(), read("cthoughts/" + transcript.id() + ".ttl"));
        }

        this.expected = MAPPER.readTree(read("expected-projection.json"));
    }

    public List<Transcript> transcripts() {
        return transcripts;
    }

    /** The questions the stub should ask about a transcript, in order. */
    public List<String> questionsFor(String transcriptId) {
        return script.get(transcriptId).stream().map(ScriptedAnswer::question).toList();
    }

    /** The scripted answers to a transcript's questions. */
    public List<ScriptedAnswer> answersFor(String transcriptId) {
        return script.get(transcriptId);
    }

    /**
     * The consolidated ontology for a transcript, looked up by its text.
     *
     * <p>By text rather than by id because that is all the stubbed interpreter
     * is given: it sees a transcription, exactly as the real one would.
     */
    public String consolidationForText(String text) {
        return consolidations.entrySet().stream()
                .filter(entry -> text.contains(entry.getKey()) || entry.getKey().contains(text))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no consolidation scripted for: " + text));
    }

    /** The transcript a piece of text belongs to. */
    public Transcript transcriptForText(String text) {
        return transcripts.stream()
                .filter(transcript -> text.contains(transcript.text()) || transcript.text().contains(text))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no transcript matches: " + text));
    }

    /** What the projection must come out as. */
    public JsonNode expected() {
        return expected;
    }

    private static String read(String name) throws IOException {
        try (InputStream in = new ClassPathResource(BASE + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * One synthetic transcription.
     *
     * @param id        its fixture id
     * @param timestamp when it was captured, with an explicit offset
     * @param text      what was said
     */
    public record Transcript(String id, String timestamp, String text) {
    }

    /**
     * One question and what the user says to it.
     *
     * @param question the question the stub asks
     * @param answer   the answer, or null when the user declines it
     */
    public record ScriptedAnswer(String question, String answer) {

        /** True when the user was asked and chose not to answer. */
        public boolean isSkipped() {
            return answer == null;
        }
    }
}
