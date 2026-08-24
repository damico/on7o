package org.on7o.server.api;

/**
 * Request body for manually asserting that an individual is also of a given type.
 *
 * @param individual local name of the individual, exactly as shown in the diagram
 * @param type       local name of the class it is additionally typed as
 */
public record TypeAssertionRequest(String individual, String type) {}
