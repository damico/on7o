package org.on7o.server.projection;

import java.time.Instant;

/**
 * What one interaction contributed to a proximity score, and why.
 *
 * <p>Kept so that a number on a screen can be explained. A person who appears
 * close should be traceable to the meetings that put them there.
 *
 * @param interactionUri the interaction
 * @param occurredAt     when it happened
 * @param type           its type token, or null when unknown
 * @param weight         what that type is worth
 * @param periodsElapsed whole decay periods between then and the instant asked about
 * @param decay          the surviving fraction after that many periods
 * @param contribution   weight times decay
 */
public record ProximityContribution(
        String interactionUri,
        Instant occurredAt,
        String type,
        double weight,
        long periodsElapsed,
        double decay,
        double contribution) {
}
