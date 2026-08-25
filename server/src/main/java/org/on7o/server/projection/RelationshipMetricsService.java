package org.on7o.server.projection;

import org.on7o.server.hcin.HcinEntity;
import org.on7o.server.hcin.HcinRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Brings the relational and financial measurements together into one reading of
 * the network at one instant.
 *
 * <p>Kept apart from anything that draws: what comes out is distances, sizes and
 * scores, not coordinates. Where a node actually lands is a layout decision, and
 * layout is not knowledge.
 */
@Service
public class RelationshipMetricsService {

    private final HcinRepository repository;
    private final InteractionProximityService proximity;
    private final VisualDistanceNormalizer distances;
    private final FinancialMetricsService financial;
    private final NodeRadiusScale radius;
    private final ProjectionProperties properties;

    public RelationshipMetricsService(HcinRepository repository,
                                      InteractionProximityService proximity,
                                      VisualDistanceNormalizer distances,
                                      FinancialMetricsService financial,
                                      NodeRadiusScale radius,
                                      ProjectionProperties properties) {
        this.repository = repository;
        this.proximity = proximity;
        this.distances = distances;
        this.financial = financial;
        this.radius = radius;
        this.properties = properties;
    }

    /**
     * Metrics for everyone the ego is connected to, as of an instant.
     *
     * <p>Includes people the ego has money with but has not interacted with, and
     * people interacted with but never paid: either is a real relationship, and
     * leaving one out would make the picture flatter than the network is.
     *
     * @param asOf the moment being asked about
     * @return metrics keyed by entity URI, the ego itself excluded
     */
    public Map<String, RelationshipMetrics> metrics(Instant asOf) {
        String ego = repository.ego();

        Map<String, ProximityScore> scores = proximity.scores(asOf);
        Map<String, Double> visualDistances = distances.distancesFor(scores);
        Map<String, FinancialMagnitude> magnitudes = financial.magnitudes(asOf);
        Map<String, FinancialAuthorityState> authorities = financial.authorityStates(asOf);

        Set<String> subjects = new LinkedHashSet<>();
        subjects.addAll(scores.keySet());
        subjects.addAll(magnitudes.keySet());
        subjects.addAll(authorities.keySet());
        repository.people().stream().map(HcinEntity::uri).forEach(subjects::add);
        subjects.remove(ego);

        Map<String, RelationshipMetrics> metrics = new LinkedHashMap<>();
        for (String subject : subjects) {
            metrics.put(subject, metricsFor(subject, scores, visualDistances, magnitudes, authorities));
        }
        return metrics;
    }

    /**
     * What changed between two instants, one entry per entity known at either.
     *
     * <p>Both instants are given: nothing here reads the clock, so asking what
     * moved between two dates last year works exactly as asking what moved this
     * week.
     *
     * @param from the earlier instant
     * @param to   the later instant
     * @return the difference per entity, largest change in closeness first
     */
    public List<RelationshipVectorDelta> deltas(Instant from, Instant to) {
        Map<String, RelationshipMetrics> before = metrics(from);
        Map<String, RelationshipMetrics> after = metrics(to);

        Set<String> subjects = new LinkedHashSet<>(after.keySet());
        subjects.addAll(before.keySet());

        Map<String, String> labels = new LinkedHashMap<>();
        repository.people().forEach(entity -> labels.putIfAbsent(entity.uri(), entity.label()));
        repository.organizations().forEach(entity -> labels.putIfAbsent(entity.uri(), entity.label()));

        return subjects.stream()
                .map(uri -> RelationshipVectorDelta.between(
                        uri,
                        labels.get(uri),
                        vectorOf(before.get(uri)),
                        vectorOf(after.get(uri))))
                .sorted(Comparator.comparingDouble(
                                (RelationshipVectorDelta delta) -> Math.abs(
                                        delta.interactionProximity() == null
                                                ? 0 : delta.interactionProximity()))
                        .reversed())
                .toList();
    }

    /** An entity the ego had no relationship with at that instant has an empty vector. */
    private static RelationshipVector vectorOf(RelationshipMetrics metrics) {
        return metrics == null ? RelationshipVector.empty() : metrics.vector();
    }

    private RelationshipMetrics metricsFor(String uri,
                                           Map<String, ProximityScore> scores,
                                           Map<String, Double> visualDistances,
                                           Map<String, FinancialMagnitude> magnitudes,
                                           Map<String, FinancialAuthorityState> authorities) {

        ProximityScore score = scores.getOrDefault(uri, ProximityScore.none(uri));
        FinancialMagnitude magnitude = magnitudes.getOrDefault(uri,
                FinancialMagnitude.none(properties.getFinancial().getBaseCurrency()));
        FinancialAuthorityState authority = authorities.getOrDefault(uri, FinancialAuthorityState.NONE);

        double distance = visualDistances.getOrDefault(uri,
                properties.getTemporalProximity().getMaxDistance());
        double nodeRadius = radius.radiusFor(magnitude.gross());

        RelationshipVector vector = new RelationshipVector(
                score.proximity(),
                magnitude.gross(),
                authority.isHeld() ? 1.0 : 0.0,
                null,
                null);

        return new RelationshipMetrics(uri, score, distance, magnitude, authority, nodeRadius, vector);
    }
}
