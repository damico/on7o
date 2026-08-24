package org.on7o.server.clarification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.on7o.server.ingest.StorageProperties;
import org.on7o.server.ingest.Thought;
import org.on7o.server.ingest.ThoughtService;
import org.on7o.server.ingest.ThoughtStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The clarification lifecycle: questions keep their identity, answers reference
 * it, and nothing the user said is lost when they change their mind or when the
 * thought is analyzed again.
 */
class ClarificationServiceTest {

    private static final List<String> TEXTS = List.of(
            "Bob pertence a ACME?",
            "Qual contrato foi discutido?",
            "Quando isso aconteceu?");

    private ThoughtStore store;
    private ClarificationService clarification;
    private String thoughtId;

    @BeforeEach
    void setUp(@TempDir Path root) throws IOException {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(root.toString());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        store = new ThoughtStore(properties, mapper);
        clarification = new ClarificationService(new ClarificationStore(store, mapper));

        thoughtId = new ThoughtService(store)
                .ingestText("Almocei com o Bob.", Instant.parse("2026-08-24T15:30:00Z"),
                        Thought.SOURCE_SYNTHETIC, null)
                .id();
    }

    @Test
    void givesEveryQuestionItsOwnStableId() throws IOException {
        List<ClarificationQuestion> questions = clarification.replaceQuestions(thoughtId, TEXTS);

        assertThat(questions).hasSize(3);
        assertThat(questions).extracting(ClarificationQuestion::id).doesNotHaveDuplicates();
        assertThat(questions).allSatisfy(q -> {
            assertThat(q.id()).startsWith("q-");
            assertThat(q.thoughtId()).isEqualTo(thoughtId);
            assertThat(q.status()).isEqualTo(QuestionStatus.OPEN);
        });

        assertThat(clarification.activeQuestions(thoughtId))
                .extracting(ClarificationQuestion::id)
                .isEqualTo(questions.stream().map(ClarificationQuestion::id).toList());
    }

    @Test
    void acceptsAnswersInAnyOrderAndAFewAtATime() throws IOException {
        List<ClarificationQuestion> questions = clarification.replaceQuestions(thoughtId, TEXTS);
        String first = questions.get(0).id();
        String third = questions.get(2).id();

        clarification.submitAnswers(thoughtId, List.of(
                new AnswerSubmission(third, "Ontem.", false),
                new AnswerSubmission(first, "Sim, trabalha na ACME.", false)));

        Map<String, AnswerRevision> answers = clarification.currentAnswers(thoughtId);
        assertThat(answers.get(first).answer()).isEqualTo("Sim, trabalha na ACME.");
        assertThat(answers.get(third).answer()).isEqualTo("Ontem.");

        assertThat(byId(first).status()).isEqualTo(QuestionStatus.ANSWERED);
        assertThat(byId(questions.get(1).id()).status()).isEqualTo(QuestionStatus.OPEN);
    }

    @Test
    void recordsASkippedQuestionAsAnsweredForBlockingButWithoutText() throws IOException {
        List<ClarificationQuestion> questions = clarification.replaceQuestions(thoughtId, TEXTS);
        String skipped = questions.get(1).id();

        clarification.submitAnswers(thoughtId, List.of(new AnswerSubmission(skipped, null, true)));

        assertThat(byId(skipped).status()).isEqualTo(QuestionStatus.SKIPPED);
        assertThat(clarification.currentAnswers(thoughtId).get(skipped).answer()).isNull();
        assertThat(clarification.unansweredRequiredIds(thoughtId)).doesNotContain(skipped);
    }

    @Test
    void keepsEveryVersionOfAChangedAnswer() throws IOException {
        String questionId = clarification.replaceQuestions(thoughtId, TEXTS).get(0).id();

        clarification.submitAnswers(thoughtId, List.of(new AnswerSubmission(questionId, "Talvez.", false)));
        clarification.submitAnswers(thoughtId, List.of(new AnswerSubmission(questionId, "Sim.", false)));

        assertThat(clarification.answerHistory(thoughtId))
                .extracting(AnswerRevision::answer, AnswerRevision::revision)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Talvez.", 1),
                        org.assertj.core.groups.Tuple.tuple("Sim.", 2));

        assertThat(clarification.currentAnswers(thoughtId).get(questionId).answer()).isEqualTo("Sim.");
    }

    @Test
    void refusesAnAnswerToAQuestionTheThoughtNeverAsked() throws IOException {
        clarification.replaceQuestions(thoughtId, TEXTS);

        assertThatThrownBy(() -> clarification.submitAnswers(thoughtId,
                List.of(new AnswerSubmission("q-000000", "Sim.", false))))
                .isInstanceOf(UnknownQuestionException.class)
                .hasMessageContaining("q-000000");
    }

    @Test
    void retiresOldQuestionsInsteadOfDeletingThem() throws IOException {
        List<ClarificationQuestion> first = clarification.replaceQuestions(thoughtId, TEXTS);
        clarification.submitAnswers(thoughtId,
                List.of(new AnswerSubmission(first.get(0).id(), "Sim.", false)));

        List<ClarificationQuestion> second = clarification.replaceQuestions(thoughtId, TEXTS);

        assertThat(clarification.activeQuestions(thoughtId))
                .extracting(ClarificationQuestion::id)
                .isEqualTo(second.stream().map(ClarificationQuestion::id).toList());

        assertThat(clarification.allQuestions(thoughtId)).hasSize(6);
        assertThat(clarification.allQuestions(thoughtId))
                .extracting(ClarificationQuestion::id).doesNotHaveDuplicates();
        assertThat(clarification.allQuestions(thoughtId).stream().filter(ClarificationQuestion::isObsolete))
                .hasSize(3);

        assertThat(clarification.answerHistory(thoughtId)).hasSize(1);
    }

    @Test
    void reportsOnlyRequiredQuestionsThatNobodyAnswered() throws IOException {
        List<ClarificationQuestion> questions = clarification.replaceQuestions(thoughtId, TEXTS);
        clarification.submitAnswers(thoughtId,
                List.of(new AnswerSubmission(questions.get(0).id(), "Sim.", false)));

        assertThat(clarification.unansweredRequiredIds(thoughtId))
                .containsExactly(questions.get(1).id(), questions.get(2).id());
    }

    @Test
    void alignsQuestionsWithTheirAnswersForTheConsolidationPrompt() throws IOException {
        List<ClarificationQuestion> questions = clarification.replaceQuestions(thoughtId, TEXTS);
        clarification.submitAnswers(thoughtId,
                List.of(new AnswerSubmission(questions.get(1).id(), "O contrato da ACME.", false)));

        ClarificationService.AlignedAnswers aligned = clarification.alignedForConsolidation(thoughtId);

        assertThat(aligned.questions()).isEqualTo(TEXTS);
        assertThat(aligned.answers()).containsExactly("", "O contrato da ACME.", "");
    }

    @Test
    void readsQuestionsAndAnswersWrittenByAnOlderBuild() throws IOException {
        Path dir = store.root().resolve(thoughtId);
        Files.writeString(dir.resolve("questions.json"), "[\"Quem e Bob?\", \"Onde foi?\"]");
        Files.writeString(dir.resolve("answers.json"), "[\"Um colega.\", \"\"]");

        List<ClarificationQuestion> questions = clarification.activeQuestions(thoughtId);
        assertThat(questions).extracting(ClarificationQuestion::text)
                .containsExactly("Quem e Bob?", "Onde foi?");

        Map<String, AnswerRevision> answers = clarification.currentAnswers(thoughtId);
        assertThat(answers.get(questions.get(0).id()).answer()).isEqualTo("Um colega.");
        assertThat(answers.get(questions.get(1).id()).skipped()).isTrue();
    }

    private ClarificationQuestion byId(String questionId) {
        return clarification.allQuestions(thoughtId).stream()
                .filter(q -> q.id().equals(questionId))
                .findFirst()
                .orElseThrow();
    }
}
