package org.on7o.server.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.on7o.server.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates the three-stage LLM interpretation pipeline.
 *
 * <p>Each stage has its own prompt (defined in {@link Constants}) and produces
 * OWL 2 Turtle output. The stages are intentionally separated so a failure in
 * any one of them does not affect the others and each artifact can be stored
 * and re-used independently.
 *
 * <ul>
 *   <li>Stage 1 - {@link #interpretRaw}: transcription to raw ontology (rThought)</li>
 *   <li>Stage 2 - {@link #questionRaw}: rThought to clarification questions (qThought)</li>
 *   <li>Stage 3 - {@link #consolidate}: rThought + answers to final ontology (cThought)</li>
 * </ul>
 */
@Service
public class ThoughtInterpreter {

    private static final Logger log = LoggerFactory.getLogger(ThoughtInterpreter.class);

    private static final Pattern JSON_BLOCK =
            Pattern.compile("```json\\s*(.*?)\\s*```", Pattern.DOTALL);
    private static final Pattern TURTLE_BLOCK =
            Pattern.compile("```turtle\\s*(.*?)\\s*```", Pattern.DOTALL);

    private final OpenAiClient openAi;
    private final ObjectMapper objectMapper;

    public ThoughtInterpreter(OpenAiClient openAi, ObjectMapper objectMapper) {
        this.openAi = openAi;
        this.objectMapper = objectMapper;
    }

    /**
     * Stage 1: extract raw semantic ontology from a transcription.
     *
     * @param transcription plain text from STT
     * @return OWL 2 Turtle string (rThought)
     */
    public String interpretRaw(String transcription) {
        log.info("rThought: starting raw extraction ({} chars)", transcription.length());
        String response = openAi.complete(Constants.PROMPT_RTHOUGHT,
                "Transcription:\n" + transcription);
        log.info("rThought: received {} chars", response.length());
        return response;
    }

    /**
     * Stage 2: generate clarification questions from the raw ontology.
     *
     * @param rThoughtTurtle the OWL Turtle produced by stage 1
     * @return parsed questions and their Turtle representation
     */
    public QThoughtResult questionRaw(String rThoughtTurtle) {
        log.info("qThought: generating questions from rThought ({} chars)", rThoughtTurtle.length());
        String response = openAi.complete(Constants.PROMPT_QTHOUGHT,
                "rThought (OWL Turtle):\n" + rThoughtTurtle);
        log.info("qThought: received {} chars", response.length());
        return parseQThought(response);
    }

    /**
     * Stage 3: consolidate rThought with user answers into a final ontology.
     *
     * @param rThought  OWL Turtle from stage 1
     * @param qThought  OWL Turtle from stage 2
     * @param questions plain-language questions shown to the user
     * @param answers   the user's plain-language answers, parallel to {@code questions}
     * @return OWL 2 Turtle string (cThought)
     */
    public String consolidate(String rThought, String qThought,
                              List<String> questions, List<String> answers) {
        String userContent = buildConsolidateMessage(rThought, qThought, questions, answers);
        log.info("cThought: starting consolidation ({} chars input)", userContent.length());
        String response = openAi.complete(Constants.PROMPT_CTHOUGHT, userContent);
        log.info("cThought: received {} chars", response.length());
        return response;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private QThoughtResult parseQThought(String response) {
        String turtle = extractBlock(response, TURTLE_BLOCK);
        if (turtle == null) {
            turtle = response;
            log.warn("qThought: no ```turtle``` block found, using full response as Turtle");
        }

        List<String> questions = new ArrayList<>();
        String jsonBlock = extractBlock(response, JSON_BLOCK);
        if (jsonBlock != null) {
            try {
                JsonNode root = objectMapper.readTree(jsonBlock);
                JsonNode arr = root.get("questions");
                if (arr != null && arr.isArray()) {
                    for (JsonNode item : arr) {
                        questions.add(item.asText());
                    }
                }
            } catch (Exception e) {
                log.warn("qThought: could not parse JSON block: {}", e.getMessage());
            }
        } else {
            log.warn("qThought: no ```json``` block found, questions list will be empty");
        }

        return new QThoughtResult(questions, turtle);
    }

    private String extractBlock(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String buildConsolidateMessage(String rThought, String qThought,
                                           List<String> questions, List<String> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append("rThought (OWL Turtle):\n").append(rThought).append("\n\n");
        sb.append("qThought (OWL Turtle):\n").append(qThought).append("\n\n");
        sb.append("User answers:\n");
        for (int i = 0; i < questions.size(); i++) {
            String q = questions.get(i);
            String a = (i < answers.size()) ? answers.get(i) : "(no answer)";
            sb.append(i + 1).append(". Q: ").append(q).append("\n");
            sb.append("   A: ").append(a).append("\n");
        }
        return sb.toString();
    }
}
