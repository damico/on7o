package org.on7o.server.projection;

import org.on7o.server.hcin.HcinInteraction;
import org.on7o.server.hcin.HcinRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How close each person is to the ego, from the interaction history alone.
 *
 * <p>Implements the projection's proximity function: the sum over interactions
 * of the interaction's weight times the decay over the periods since it
 * happened. Someone seen weekly stays close; someone seen once a quarter drifts
 * away; a single recent meeting pulls them back in for a while.
 *
 * <p>Every calculation takes the instant it is being made for. Nothing here
 * reads the clock, which is what lets the same code answer for last year as
 * easily as for now, and what lets tests be about the model rather than about
 * when they happen to run.
 */
@Service
public class InteractionProximityService {

    private final HcinRepository repository;
    private final InteractionWeightPolicy weights;
    private final TemporalDecayPolicy decay;

    public InteractionProximityService(HcinRepository repository,
                                       InteractionWeightPolicy weights,
                                       TemporalDecayPolicy decay) {
        this.repository = repository;
        this.weights = weights;
        this.decay = decay;
    }

    /**
     * Proximity of everyone the ego has ever interacted with, as of an instant.
     *
     * @param asOf the moment being asked about; later interactions do not exist yet
     * @return one score per person, keyed by URI
     */
    public Map<String, ProximityScore> scores(Instant asOf) {
        Map<String, List<ProximityContribution>> byPerson = new LinkedHashMap<>();

        for (HcinInteraction interaction : repository.interactionsWithEgo(asOf)) {
            if (interaction.occurredAt() == null) {
                continue;
            }
            byPerson.computeIfAbsent(interaction.otherUri(), person -> new ArrayList<>())
                    .add(contributionOf(interaction, asOf));
        }

        Map<String, ProximityScore> scores = new LinkedHashMap<>();
        byPerson.forEach((person, contributions) -> {
            List<ProximityContribution> sorted = contributions.stream()
                    .sorted(Comparator.comparing(ProximityContribution::occurredAt).reversed())
                    .toList();
            double total = sorted.stream().mapToDouble(ProximityContribution::contribution).sum();
            scores.put(person, new ProximityScore(person, total, sorted));
        });

        return scores;
    }

    /** Proximity of one person, or a zero score when they have never been met. */
    public ProximityScore scoreOf(String personUri, Instant asOf) {
        return scores(asOf).getOrDefault(personUri, ProximityScore.none(personUri));
    }

    private ProximityContribution contributionOf(HcinInteraction interaction, Instant asOf) {
        double weight = weights.weightOf(interaction);
        long periods = decay.periodsBetween(interaction.occurredAt(), asOf);
        double surviving = decay.decayOver(periods);

        return new ProximityContribution(
                interaction.uri(),
                interaction.occurredAt(),
                interaction.type(),
                weight,
                periods,
                surviving,
                weight * surviving);
    }
}
