package org.on7o.server.projection;

import org.on7o.server.hcin.FlowDirection;
import org.on7o.server.hcin.HcinFinancialAuthority;
import org.on7o.server.hcin.HcinFinancialFlow;
import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.hcin.AuthorityType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the money says about each of the ego's relationships, at one instant.
 *
 * <p>Amounts are {@link BigDecimal} from the dataset all the way out. Money that
 * cannot be added exactly is not money, and a projection is not a good enough
 * reason to start rounding it.
 *
 * <p>Nothing is converted between currencies. The headline figures cover the
 * base currency and anything else is reported beside them, because this system
 * has no exchange rates and a made-up one would be a lie with a decimal point
 * on it.
 */
@Service
public class FinancialMetricsService {

    private final HcinRepository repository;
    private final ProjectionProperties properties;

    public FinancialMetricsService(HcinRepository repository, ProjectionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Financial magnitude per counterparty, as of an instant.
     *
     * @param asOf the moment being asked about; later flows do not exist yet
     * @return one magnitude per person or organization the ego exchanged money with
     */
    public Map<String, FinancialMagnitude> magnitudes(Instant asOf) {
        String ego = repository.ego();
        String base = properties.getFinancial().getBaseCurrency();

        Map<String, Accumulator> byCounterparty = new LinkedHashMap<>();

        for (HcinFinancialFlow flow : repository.flowsInvolvingEgo(asOf)) {
            if (flow.amount() == null) {
                continue;
            }
            byCounterparty
                    .computeIfAbsent(flow.counterparty(ego), key -> new Accumulator(base))
                    .add(flow);
        }

        Map<String, FinancialMagnitude> magnitudes = new LinkedHashMap<>();
        byCounterparty.forEach((counterparty, accumulator) ->
                magnitudes.put(counterparty, accumulator.toMagnitude()));
        return magnitudes;
    }

    /** Financial magnitude for one counterparty, zero when no money ever moved. */
    public FinancialMagnitude magnitudeOf(String counterpartyUri, Instant asOf) {
        return magnitudes(asOf).getOrDefault(counterpartyUri,
                FinancialMagnitude.none(properties.getFinancial().getBaseCurrency()));
    }

    /**
     * Who holds what decision power, as of an instant.
     *
     * <p>Authority that had ended by then is not returned: someone who could
     * approve payments last year cannot approve them in a projection of today.
     */
    public Map<String, FinancialAuthorityState> authorityStates(Instant asOf) {
        Map<String, Set<AuthorityType>> byHolder = new LinkedHashMap<>();

        for (HcinFinancialAuthority authority : repository.authorities(asOf)) {
            if (authority.holderUri() == null) {
                continue;
            }
            Set<AuthorityType> types = byHolder.computeIfAbsent(
                    authority.holderUri(), holder -> EnumSet.noneOf(AuthorityType.class));
            if (authority.type() != null) {
                types.add(authority.type());
            }
        }

        Map<String, FinancialAuthorityState> states = new LinkedHashMap<>();
        byHolder.forEach((holder, types) -> states.put(holder, FinancialAuthorityState.of(types)));
        return states;
    }

    /** Adds up flows for one counterparty, keeping the currencies apart. */
    private static final class Accumulator {

        private final String baseCurrency;
        private final Map<String, BigDecimal> grossByCurrency = new LinkedHashMap<>();
        private final Set<String> otherCurrencies = new LinkedHashSet<>();

        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal inflow = BigDecimal.ZERO;
        private BigDecimal outflow = BigDecimal.ZERO;

        private Accumulator(String baseCurrency) {
            this.baseCurrency = baseCurrency;
        }

        private void add(HcinFinancialFlow flow) {
            BigDecimal amount = flow.amount().abs();
            String currency = flow.currency() == null ? baseCurrency : flow.currency();

            grossByCurrency.merge(currency, amount, BigDecimal::add);

            if (!baseCurrency.equals(currency)) {
                otherCurrencies.add(currency);
                return;
            }

            gross = gross.add(amount);
            if (flow.direction() == FlowDirection.INFLOW) {
                inflow = inflow.add(amount);
            } else if (flow.direction() == FlowDirection.OUTFLOW) {
                outflow = outflow.add(amount);
            }
        }

        private FinancialMagnitude toMagnitude() {
            return new FinancialMagnitude(
                    baseCurrency,
                    gross,
                    inflow,
                    outflow,
                    inflow.subtract(outflow),
                    Map.copyOf(grossByCurrency),
                    Set.copyOf(otherCurrencies));
        }
    }
}
