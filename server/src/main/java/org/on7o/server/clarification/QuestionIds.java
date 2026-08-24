package org.on7o.server.clarification;

import java.util.Collection;

/**
 * Generates clarification question ids.
 *
 * <p>Ids are derived from the thought, the question text and its position rather
 * than drawn at random, so re-reading the same input produces the same ids and a
 * stored answer never loses the question it belongs to.
 */
final class QuestionIds {

    private static final String PREFIX = "q-";
    private static final int MASK = 0x00FFFFFF;

    private QuestionIds() {
    }

    /** A stable id for one question of one thought. */
    static String of(String thoughtId, String text, int index) {
        return format(thoughtId + "|" + index + "|" + text);
    }

    /**
     * A stable id that does not collide with ids already in use, salting the
     * seed until it is free. Collisions are vanishingly rare at the handful of
     * questions a thought produces, but an id that is not unique would silently
     * misroute an answer.
     */
    static String uniqueOf(String thoughtId, String text, int index, Collection<String> taken) {
        String candidate = of(thoughtId, text, index);
        for (int salt = 1; taken.contains(candidate); salt++) {
            candidate = format(thoughtId + "|" + index + "|" + salt + "|" + text);
        }
        return candidate;
    }

    private static String format(String seed) {
        return PREFIX + String.format("%06x", seed.hashCode() & MASK);
    }
}
