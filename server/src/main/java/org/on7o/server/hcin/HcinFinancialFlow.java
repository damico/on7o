package org.on7o.server.hcin;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Money moving between two entities at a point in time.
 *
 * <p>The amount is a {@link BigDecimal} throughout: money that cannot be added
 * exactly is not money.
 *
 * @param uri        stable HCIN URI
 * @param sourceUri  who the money came from
 * @param targetUri  who it went to
 * @param direction  which way it moved relative to the ego
 * @param amount     how much, never negative; the sign lives in the direction
 * @param currency   ISO 4217 code
 * @param occurredAt when it happened
 * @param tier       how strongly it is believed
 */
public record HcinFinancialFlow(
        String uri,
        String sourceUri,
        String targetUri,
        FlowDirection direction,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        KnowledgeTier tier) {

    /** The entity on the far side of the flow from the ego. */
    public String counterparty(String egoUri) {
        return egoUri.equals(sourceUri) ? targetUri : sourceUri;
    }
}
