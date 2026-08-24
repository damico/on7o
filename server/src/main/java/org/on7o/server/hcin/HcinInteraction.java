package org.on7o.server.hcin;

import java.time.Instant;

/**
 * One event between the ego and someone else.
 *
 * <p>Interactions are the evidence proximity is computed from, which is why the
 * timestamp is not optional here: an interaction that cannot be placed in time
 * cannot decay.
 *
 * @param uri        stable HCIN URI
 * @param otherUri   the participant who is not the ego
 * @param occurredAt when it happened
 * @param type       token such as meeting or phoneCall, or null when unknown
 * @param contextUri what it was about, or null
 * @param tier       how strongly it is believed
 */
public record HcinInteraction(
        String uri,
        String otherUri,
        Instant occurredAt,
        String type,
        String contextUri,
        KnowledgeTier tier) {
}
