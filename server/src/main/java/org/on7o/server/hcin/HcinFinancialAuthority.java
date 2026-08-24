package org.on7o.server.hcin;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Someone's power to decide about money, inside an organization, over a period,
 * within a scope.
 *
 * <p>A null scope means the scope is unknown, never that it is unlimited. That
 * distinction is the whole reason this is a record with its own validity rather
 * than a boolean on a person.
 *
 * @param uri             stable HCIN URI
 * @param holderUri       who holds it
 * @param organizationUri where they hold it
 * @param type            over money going out or coming in
 * @param scope           what it covers, or null when unknown
 * @param spendingLimit   the ceiling, or null when unknown
 * @param currency        currency of the limit, or null
 * @param validFrom       when it began, or null when unknown
 * @param validTo         when it ended, or null when it still holds
 * @param tier            how strongly it is believed
 */
public record HcinFinancialAuthority(
        String uri,
        String holderUri,
        String organizationUri,
        AuthorityType type,
        String scope,
        BigDecimal spendingLimit,
        String currency,
        Instant validFrom,
        Instant validTo,
        KnowledgeTier tier) {
}
