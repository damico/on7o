package org.on7o.server.api;

/**
 * Response returned after a new entity-derived thought is created and questioned.
 *
 * @param id       the new thought's id
 * @param redirect URL of its questions page
 */
public record EntityDeriveResponse(String id, String redirect) {}
