package org.on7o.server.api;

/**
 * Request body for deriving a new thought from a single entity node.
 *
 * @param stage the stage whose diagram the entity was picked from ("rthought", "qthought" or "cthought")
 * @param label the entity's display label, exactly as shown in the diagram
 */
public record EntityDeriveRequest(String stage, String label) {}
