package org.on7o.server.llm;

/**
 * An entity found in a cThought that is referenced but not itself defined,
 * proposed as a candidate for its own derived eThought.
 *
 * @param label  the entity's short display label (e.g. "Psychiatrist")
 * @param reason why the entity was flagged as needing its own definition
 */
public record EntityCandidate(String label, String reason) {}
