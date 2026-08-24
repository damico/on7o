package org.on7o.server.hcin;

import java.time.Instant;

/**
 * A person belonging to an organization over a period.
 *
 * @param uri             stable HCIN URI of the membership itself
 * @param personUri       who belongs
 * @param organizationUri what they belong to
 * @param role            what they do there, or null when unknown
 * @param validFrom       when it started in the world, or null when unknown
 * @param validTo         when it ended, or null when it still holds
 * @param tier            how strongly it is believed
 */
public record HcinMembership(
        String uri,
        String personUri,
        String organizationUri,
        String role,
        Instant validFrom,
        Instant validTo,
        KnowledgeTier tier) {
}
