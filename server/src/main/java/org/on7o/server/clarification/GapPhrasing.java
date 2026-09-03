package org.on7o.server.clarification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a gap in the network into a question a person can answer.
 *
 * <p>A SHACL message is written for whoever reads a validation report: it names
 * the missing property and says what it is for. A question is a different piece
 * of writing, addressed to the user, in the second person, about something they
 * said. This holds the translation between the two, one entry per property the
 * shapes can report as missing.
 *
 * <p>Each entry says two things: how to put the question, and how it expects to
 * be answered. Both depend on what the property means rather than on how it is
 * spelled, which is why the mapping is written out instead of derived. A missing
 * date is a date, a missing layer is one of the layers the ontology declares, and
 * a missing context is a setting the network may or may not have seen before.
 *
 * <p>A property with no entry falls back to the shape's own message as free text,
 * which is worse writing but never silence.
 */
final class GapPhrasing {

    private static final String HCIN = "http://on7o.io/hcin#";
    private static final String FINANCIAL = "http://on7o.io/hcin/financial#";

    /** Property URI to how the question is put, where {@code %s} is the node's description. */
    private static final Map<String, Phrasing> PHRASINGS = phrasings();

    private GapPhrasing() {
    }

    private static Map<String, Phrasing> phrasings() {
        Map<String, Phrasing> phrasings = new LinkedHashMap<>();
        phrasings.put(HCIN + "layer", new Phrasing(
                "Which layers does the %s live in? A tie can be in more than one.",
                AnswerKind.MULTI_CHOICE));
        phrasings.put(HCIN + "context", new Phrasing(
                "What settings does the %s belong to: which project, contract, "
                + "organization or occasion? It can belong to more than one.",
                AnswerKind.MULTI_SUGGESTION));
        phrasings.put(HCIN + "occurredAt", new Phrasing(
                "When did the %s happen?",
                AnswerKind.DATE));
        phrasings.put(HCIN + "validFrom", new Phrasing(
                "When did the %s begin?",
                AnswerKind.DATE));
        phrasings.put(FINANCIAL + "amount", new Phrasing(
                "How much money was involved in the %s?",
                AnswerKind.NUMBER));
        phrasings.put(FINANCIAL + "currency", new Phrasing(
                "Which currency was the %s in?",
                AnswerKind.SUGGESTION));
        phrasings.put(FINANCIAL + "scope", new Phrasing(
                "What does the %s cover? Saying nothing would leave it recorded as "
                + "unrestricted, which it probably is not.",
                AnswerKind.MULTI_SUGGESTION));
        return phrasings;
    }

    /**
     * How to ask about one missing property.
     *
     * @param property    URI of the property the shapes found missing
     * @param description a short human phrase for the node, from the repository
     * @param fallback    the shape's own message, used when the property is unmapped
     */
    static Phrasing of(String property, String description, String fallback) {
        Phrasing phrasing = PHRASINGS.get(property);
        return phrasing == null
                ? new Phrasing(description + ": " + fallback, AnswerKind.TEXT)
                : new Phrasing(phrasing.text().formatted(description), phrasing.kind());
    }

    /**
     * One question, worded and typed.
     *
     * @param text the question, either a template or the finished sentence
     * @param kind how it expects to be answered
     */
    record Phrasing(String text, AnswerKind kind) {
    }
}
