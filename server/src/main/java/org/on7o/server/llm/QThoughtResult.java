package org.on7o.server.llm;

import java.util.List;

/**
 * Result of the qThought stage: clarification questions in two representations.
 *
 * @param questions plain-language questions for the user
 * @param turtle    OWL 2 Turtle encoding the same questions as on7o:ClarificationQuestion instances
 */
public record QThoughtResult(List<String> questions, String turtle) {}
