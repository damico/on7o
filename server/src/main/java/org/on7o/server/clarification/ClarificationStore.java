package org.on7o.server.clarification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.on7o.server.ingest.ThoughtStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for clarification questions and answers, inside the directory of
 * the thought they belong to.
 *
 * <p>Two files, each with one job:
 * <ul>
 *   <li>{@code questions.json} - every question ever generated for the thought,
 *       including the ones a re-analysis made obsolete;</li>
 *   <li>{@code answers.json} - an append-only log of answer revisions.</li>
 * </ul>
 *
 * <p>Both files used to hold plain string arrays, positionally aligned with each
 * other. Those are still readable: they are converted on the way in, so a store
 * written by an older build keeps working and is upgraded the next time it is
 * saved.
 */
@Service
public class ClarificationStore {

    private static final Logger log = LoggerFactory.getLogger(ClarificationStore.class);

    private static final TypeReference<List<ClarificationQuestion>> QUESTION_LIST =
            new TypeReference<>() {};
    private static final TypeReference<List<AnswerRevision>> ANSWER_LIST =
            new TypeReference<>() {};

    private final ThoughtStore thoughts;
    private final ObjectMapper objectMapper;

    public ClarificationStore(ThoughtStore thoughts, ObjectMapper objectMapper) {
        this.thoughts = thoughts;
        this.objectMapper = objectMapper;
    }

    /** Every question of a thought, obsolete ones included, in the order generated. */
    public List<ClarificationQuestion> findQuestions(String thoughtId) {
        if (isLegacyStringArray(thoughtId, ThoughtStore.QUESTIONS_FILE)) {
            return legacyQuestions(thoughtId);
        }
        return thoughts.readJson(thoughtId, ThoughtStore.QUESTIONS_FILE, QUESTION_LIST)
                .orElseGet(List::of);
    }

    /** Replaces the stored question list. */
    public void saveQuestions(String thoughtId, List<ClarificationQuestion> questions)
            throws IOException {
        thoughts.saveJson(thoughtId, ThoughtStore.QUESTIONS_FILE, questions);
    }

    /** Every answer revision of a thought, oldest first. */
    public List<AnswerRevision> findAnswerRevisions(String thoughtId) {
        if (isLegacyStringArray(thoughtId, ThoughtStore.ANSWERS_FILE)) {
            return legacyAnswers(thoughtId);
        }
        return thoughts.readJson(thoughtId, ThoughtStore.ANSWERS_FILE, ANSWER_LIST)
                .orElseGet(List::of);
    }

    /** Replaces the stored answer log. Callers append to what {@link #findAnswerRevisions} returned. */
    public void saveAnswerRevisions(String thoughtId, List<AnswerRevision> revisions)
            throws IOException {
        thoughts.saveJson(thoughtId, ThoughtStore.ANSWERS_FILE, revisions);
    }

    /** True when the thought has a question list at all. */
    public boolean hasQuestions(String thoughtId) {
        return thoughts.hasFile(thoughtId, ThoughtStore.QUESTIONS_FILE);
    }

    // -------------------------------------------------------------------------
    // Reading what older builds wrote
    // -------------------------------------------------------------------------

    /** True when the file exists and its first element is a bare string. */
    private boolean isLegacyStringArray(String thoughtId, String filename) {
        return firstElement(thoughtId, filename).map(JsonNode::isTextual).orElse(false);
    }

    private Optional<JsonNode> firstElement(String thoughtId, String filename) {
        return thoughts.readJson(thoughtId, filename, new TypeReference<JsonNode>() {})
                .filter(JsonNode::isArray)
                .filter(node -> !node.isEmpty())
                .map(node -> node.get(0));
    }

    /**
     * Rebuilds questions from a plain string array. Ids are generated from the
     * text, so the same legacy file always yields the same ids and any answers
     * migrated alongside it keep pointing at the right question.
     */
    private List<ClarificationQuestion> legacyQuestions(String thoughtId) {
        List<String> texts = readStrings(thoughtId, ThoughtStore.QUESTIONS_FILE);
        List<ClarificationQuestion> questions = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            questions.add(new ClarificationQuestion(
                    QuestionIds.of(thoughtId, texts.get(i), i),
                    thoughtId,
                    texts.get(i),
                    null,
                    null,
                    true,
                    QuestionStatus.OPEN,
                    Instant.EPOCH));
        }
        log.info("migrated {} legacy question(s) for thought {}", questions.size(), thoughtId);
        return questions;
    }

    /**
     * Rebuilds answers from a plain string array, whose only link to a question
     * was its position. Anything past the end of the question list is dropped:
     * an answer that cannot be attributed to a question is not evidence of
     * anything.
     */
    private List<AnswerRevision> legacyAnswers(String thoughtId) {
        List<String> texts = readStrings(thoughtId, ThoughtStore.ANSWERS_FILE);
        List<ClarificationQuestion> questions = findQuestions(thoughtId);

        List<AnswerRevision> revisions = new ArrayList<>();
        for (int i = 0; i < Math.min(texts.size(), questions.size()); i++) {
            String answer = texts.get(i);
            boolean skipped = answer == null || answer.isBlank();
            revisions.add(new AnswerRevision(
                    questions.get(i).id(), skipped ? null : answer, skipped, Instant.EPOCH, 1));
        }
        log.info("migrated {} legacy answer(s) for thought {}", revisions.size(), thoughtId);
        return revisions;
    }

    private List<String> readStrings(String thoughtId, String filename) {
        return thoughts.readJson(thoughtId, filename, new TypeReference<List<String>>() {})
                .orElseGet(List::of);
    }
}
