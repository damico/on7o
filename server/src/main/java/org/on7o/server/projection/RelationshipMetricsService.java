package org.on7o.server.projection;

import org.on7o.server.hcin.HcinEntity;
import org.on7o.server.hcin.HcinRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
