package org.on7o.server.projection;

import java.math.BigDecimal;
import java.util.Map;

/**
 * How much money a relationship moves, and which way.
 *
 * <p>Gross and net answer different questions and are both kept. A relationship
 * carrying a million in and almost a million out is a large financial
 * relationship with a small balance, and a projection that only knew the balance
 * would draw it as if it barely existed.
 *
 * @param currency      the currency the headline figures are in
 * @param gross         the sum of every amount, regardless of direction
 * @param inflow        what came in
 * @param outflow       what went out
 * @param net           inflow minus outflow, which may be negative
 * @param byCurrency    gross per currency, for everything the flows were stated in
 * @param unconverted   currencies left out of the headline figures, having no rate to convert with
 */
public record FinancialMagnitude(
        String currency,
        BigDecimal gross,
        BigDecimal inflow,
        BigDecimal outflow,
        BigDecimal net,
        Map<String, BigDecimal> byCurrency,
        java.util.Set<String> unconverted) {

    /** A relationship with no money in it. */
    public static FinancialMagnitude none(String currency) {
        return new FinancialMagnitude(currency, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, Map.of(), java.util.Set.of());
    }

    /** True when money moved at all. */
    public boolean isEmpty() {
        return gross.signum() == 0;
    }
}
