package org.on7o.server.clarification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The clarification lifecycle: which questions a thought is asking, what the
 * user answered, and what is still missing.
 *
 * <p>Everything here is keyed by question id. Neither the order questions were
 * generated in nor the order answers arrive in affects the result, and answers
 * may arrive a few at a time.
 */
@Service
public class ClarificationService {

    private static final Logger log = LoggerFactory.getLogger(ClarificationService.class);

    /** What a question with no answer contributes to the consolidation prompt. */
    private static final String NO_ANSWER = "";

    private final ClarificationStore store;

    public ClarificationService(ClarificationStore store) {
        this.store = store;
    }

    /** Every question of a thought, obsolete ones included. */
    public List<ClarificationQuestion> allQuestions(String thoughtId) {
        return store.findQuestions(thoughtId);
    }

    /** The questions still being asked, in the order they were generated. */
    public List<ClarificationQuestion> activeQuestions(String thoughtId) {
        return allQuestions(thoughtId).stream().filter(q -> !q.isObsolete()).toList();
    }

    /** True when the thought has been through question generation at all. */
    public boolean hasQuestions(String thoughtId) {
        return store.hasQuestions(thoughtId);
    }

    /**
     * Records a freshly generated set of questions, retiring whatever was asked
     * before.
     *
     * <p>The previous questions are kept and marked {@link QuestionStatus#OBSOLETE}
     * rather than deleted: they were really asked, they may have really been
     * answered, and a re-analysis is not a reason to lose that.
     *
     * @param thoughtId the thought the questions belong to
     * @param texts     plain-language questions, in priority order
     * @return the newly created questions
     */
    public List<ClarificationQuestion> replaceQuestions(String thoughtId, List<String> texts)
            throws IOException {

        List<ClarificationQuestion> retired = allQuestions(thoughtId).stream()
                .map(q -> q.isObsolete() ? q : q.withStatus(QuestionStatus.OBSOLETE))
                .toList();

        Set<String> taken = new HashSet<>(retired.stream().map(ClarificationQuestion::id).toList());
        Instant now = Instant.now();

        List<ClarificationQuestion> created = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String id = QuestionIds.uniqueOf(thoughtId, text, i, taken);
            taken.add(id);
            created.add(new ClarificationQuestion(
                    id, thoughtId, text, null, null, true, QuestionStatus.OPEN, now));
        }

        List<ClarificationQuestion> all = new ArrayList<>(retired);
        all.addAll(created);
        store.saveQuestions(thoughtId, all);

        if (!retired.isEmpty()) {
            log.info("thought {}: {} question(s) retired, {} asked", thoughtId, retired.size(), created.size());
        }
        return created;
    }

    /**
     * Adds questions to a thought without retiring the ones already there.
     *
     * <p>{@link #replaceQuestions} exists for one generator, the qThought stage,
     * which produces the whole set at once and owns it. A question that came from
     * the network is a second source asking about the same thought, and it must
     * neither retire what the first asked nor be retired by it.
     *
     * <p>Adding is idempotent through the ids the caller brings, so re-running the
     * generator never duplicates a question. A question already on the thought is
     * not added again; if it is still open, its wording is refreshed from the
     * proposal, because the id identifies the gap while the text is only the
     * current rendering of it and a better rendering is worth having.
     *
     * <p>A question the qThought stage asked freezes once it has been answered or
     * skipped: it is what the user actually read, and an answer detached from the
     * question it answered is worse than an awkward sentence. A question the
     * network raised does not, because it is identified by the node and property
     * it is about rather than by its words: re-offering it is how a question that
     * was once a blank box becomes the list of layers the ontology declares, and
     * how an answer typed by hand can be given again by clicking.
     *
     * @param thoughtId the thought the questions attach to
     * @param proposed  questions carrying ids derived from what they are about
     * @return only the questions this call actually added
     */
    public List<ClarificationQuestion> addQuestions(String thoughtId, List<ProposedQuestion> proposed)
            throws IOException {

        Map<String, ProposedQuestion> byId = new LinkedHashMap<>();
        proposed.forEach(question -> byId.putIfAbsent(question.id(), question));

        List<ClarificationQuestion> kept = new ArrayList<>();
        for (ClarificationQuestion question : allQuestions(thoughtId)) {
            ProposedQuestion again = byId.remove(question.id());
            kept.add(again == null || question.isObsolete()
                    ? question
                    : question.withOffer(again.text(), again.kind(), again.options()));
        }

        Instant now = Instant.now();
        List<ClarificationQuestion> created = byId.values().stream()
                .map(question -> new ClarificationQuestion(
                        question.id(),
                        thoughtId,
                        question.text(),
                        question.subjectRef(),
                        question.predicateRef(),
                        question.required(),
                        QuestionStatus.OPEN,
                        now,
                        question.kind(),
                        question.options()))
                .toList();

        List<ClarificationQuestion> all = new ArrayList<>(kept);
        all.addAll(created);
        store.saveQuestions(thoughtId, all);

        log.info("thought {}: {} question(s) added, {} already present",
                thoughtId, created.size(), proposed.size() - created.size());
        return created;
    }

    /**
     * A question on its way in, before it becomes one of a thought's questions.
     *
     * <p>It carries its own id because the generator is what knows when two
     * questions are the same question: the same gap, found again, must produce
     * the same id or every run would ask it afresh.
     *
     * @param id           stable id, derived from what the question is about
     * @param text         the plain-language question shown to the user
     * @param subjectRef   URI of the node the question is about
     * @param predicateRef URI of the property that is missing
     * @param required     whether consolidation should wait for it
     * @param kind         how the question expects to be answered
     * @param options      the values it offers, empty when it offers none
     */
    public record ProposedQuestion(
            String id,
            String text,
            String subjectRef,
            String predicateRef,
            boolean required,
            AnswerKind kind,
            List<String> options) {
    }

    /**
     * Records answers and moves the questions they refer to.
     *
     * <p>An answer to an already-answered question is a new revision, not an
     * overwrite. Submitting nothing for a question leaves it alone, which is what
     * makes partial submissions safe.
     *
     * @param thoughtId   the thought being clarified
     * @param submissions answers, in any order, for any subset of the questions
     * @return the thought's questions after the update
     * @throws UnknownQuestionException when an answer names a question the thought never asked
     */
    public List<ClarificationQuestion> submitAnswers(String thoughtId, List<AnswerSubmission> submissions)
            throws IOException {

        List<ClarificationQuestion> questions = allQuestions(thoughtId);
        Map<String, ClarificationQuestion> byId = new LinkedHashMap<>();
        questions.forEach(q -> byId.put(q.id(), q));

        for (AnswerSubmission submission : submissions) {
            if (!byId.containsKey(submission.questionId())) {
                throw new UnknownQuestionException(thoughtId, submission.questionId());
            }
        }

        List<AnswerRevision> revisions = new ArrayList<>(store.findAnswerRevisions(thoughtId));
        Map<String, Integer> highest = new LinkedHashMap<>();
        revisions.forEach(r -> highest.merge(r.questionId(), r.revision(), Math::max));

        Instant now = Instant.now();
        for (AnswerSubmission submission : submissions) {
            if (submission.isEmpty()) {
                continue;
            }
            int revision = highest.merge(submission.questionId(), 1, Integer::sum);
            AnswerRevision recorded = new AnswerRevision(
                    submission.questionId(),
                    submission.normalizedAnswer(),
                    submission.skipped(),
                    now,
                    revision);
            revisions.add(recorded);
            byId.computeIfPresent(submission.questionId(),
                    (id, question) -> question.withStatus(recorded.resultingStatus()));
        }

        store.saveAnswerRevisions(thoughtId, revisions);
        List<ClarificationQuestion> updated = List.copyOf(byId.values());
        store.saveQuestions(thoughtId, updated);
        return updated;
    }

    /** Every answer revision of a thought, oldest first. */
    public List<AnswerRevision> answerHistory(String thoughtId) {
        return store.findAnswerRevisions(thoughtId);
    }

    /** The current answer to each question that has one, keyed by question id. */
    public Map<String, AnswerRevision> currentAnswers(String thoughtId) {
        Map<String, AnswerRevision> current = new LinkedHashMap<>();
        for (AnswerRevision revision : answerHistory(thoughtId)) {
            AnswerRevision existing = current.get(revision.questionId());
            if (existing == null || revision.revision() >= existing.revision()) {
                current.put(revision.questionId(), revision);
            }
        }
        return current;
    }

    /**
     * Ids of the questions that still block consolidation: required, still being
     * asked, and never answered or skipped.
     */
    public List<String> unansweredRequiredIds(String thoughtId) {
        Map<String, AnswerRevision> answers = currentAnswers(thoughtId);
        return activeQuestions(thoughtId).stream()
                .filter(ClarificationQuestion::required)
                .filter(q -> !answers.containsKey(q.id()))
                .map(ClarificationQuestion::id)
                .toList();
    }

    /**
     * The active questions and their answers as two aligned lists, which is what
     * the consolidation prompt expects.
     *
     * <p>Alignment exists only inside this call. It is a detail of how the
     * prompt is written, never a contract of the API.
     */
    public AlignedAnswers alignedForConsolidation(String thoughtId) {
        Map<String, AnswerRevision> answers = currentAnswers(thoughtId);
        List<ClarificationQuestion> questions = activeQuestions(thoughtId);

        List<String> questionTexts = new ArrayList<>(questions.size());
        List<String> answerTexts = new ArrayList<>(questions.size());
        for (ClarificationQuestion question : questions) {
            questionTexts.add(question.text());
            answerTexts.add(Optional.ofNullable(answers.get(question.id()))
                    .map(AnswerRevision::answer)
                    .orElse(NO_ANSWER));
        }
        return new AlignedAnswers(questionTexts, answerTexts);
    }

    /**
     * Questions and their answers, positionally aligned for the LLM prompt.
     *
     * @param questions question texts
     * @param answers   answer texts, empty where the user did not answer
     */
    public record AlignedAnswers(List<String> questions, List<String> answers) {
    }
}
