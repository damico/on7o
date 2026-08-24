package org.on7o.server.projection;

import java.util.List;

/**
 * How close someone is to the ego at one instant.
 *
 * @param personUri     who
 * @param proximity     the sum of every interaction's decayed weight
 * @param contributions what made it up, most recent first
 */
public record ProximityScore(String personUri, double proximity, List<ProximityContribution> contributions) {

    /** Someone the ego has never interacted with, as far as the network knows. */
    public static ProximityScore none(String personUri) {
        return new ProximityScore(personUri, 0.0, List.of());
    }
}
