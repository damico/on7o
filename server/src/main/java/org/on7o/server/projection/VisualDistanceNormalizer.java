package org.on7o.server.projection;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns proximity into how far from the centre someone is drawn.
 *
 * <p>Distance is the inverse of proximity, which makes it unbounded: someone
 * never interacted with would sit infinitely far away. So the raw values are
 * squeezed into the configured band before anyone tries to lay them out.
 *
 * <p>Normalizing across the population rather than against an absolute scale
 * means the picture is always readable, and that distances are comparable within
 * one projection but not between two of them.
 */
@Service
public class VisualDistanceNormalizer {

    private final ProjectionProperties properties;

    public VisualDistanceNormalizer(ProjectionProperties properties) {
        this.properties = properties;
    }

    /** The raw, unbounded distance implied by a proximity. */
    public double rawDistance(double proximity) {
        return 1.0 / (properties.getTemporalProximity().getEpsilon() + Math.max(0, proximity));
    }

    /**
     * Raw distances mapped onto the configured band.
     *
     * <p>Mapped through a logarithm, because the raw value is the reciprocal of
     * proximity and is therefore wildly skewed: everyone seen in the last few
     * weeks lands within a hair of the near edge while one long-lost contact
     * takes up the whole rest of the band. Spacing by orders of magnitude keeps
     * the whole population readable and preserves the ordering exactly, which is
     * the part that carries meaning.
     *
     * <p>When everyone is equally close there is no nearer and no farther, so
     * they all sit at the near edge: spreading them apart would draw a
     * difference the data does not contain.
     */
    public Map<String, Double> normalize(Map<String, Double> rawDistances) {
        double min = properties.getTemporalProximity().getMinDistance();
        double max = properties.getTemporalProximity().getMaxDistance();

        Map<String, Double> normalized = new LinkedHashMap<>();
        if (rawDistances.isEmpty()) {
            return normalized;
        }

        double lowest = rawDistances.values().stream().mapToDouble(Math::log).min().orElse(0);
        double highest = rawDistances.values().stream().mapToDouble(Math::log).max().orElse(0);
        double spread = highest - lowest;

        rawDistances.forEach((person, raw) -> normalized.put(person,
                spread == 0 ? min : min + ((Math.log(raw) - lowest) / spread) * (max - min)));

        return normalized;
    }

    /** Proximities straight through to normalized visual distances. */
    public Map<String, Double> distancesFor(Map<String, ProximityScore> scores) {
        Map<String, Double> raw = new LinkedHashMap<>();
        scores.forEach((person, score) -> raw.put(person, rawDistance(score.proximity())));
        return normalize(raw);
    }
}
