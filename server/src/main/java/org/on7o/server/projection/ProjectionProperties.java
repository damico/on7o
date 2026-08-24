package org.on7o.server.projection;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The analytic and visual parameters of the financial projection.
 *
 * <p>None of this is ontology. What a meeting is worth compared to a message,
 * how fast attention decays, how big a node gets: these are choices about how to
 * read the network, and they are configuration precisely so that changing one
 * never means changing what the HCIN claims is true.
 */
@ConfigurationProperties(prefix = "on7o.projection")
public class ProjectionProperties {

    private final TemporalProximity temporalProximity = new TemporalProximity();
    private final Financial financial = new Financial();

    /**
     * What each kind of interaction contributes, keyed by the interaction type
     * token. A type that is not listed falls back to {@link #defaultWeight}.
     */
    private Map<String, Double> interactionWeights = defaults();

    /** Weight of an interaction whose type is unknown or unlisted. */
    private double defaultWeight = 0.1;

    private static Map<String, Double> defaults() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("mention", 0.1);
        weights.put("message", 0.3);
        weights.put("phoneCall", 0.6);
        weights.put("meeting", 1.0);
        weights.put("strategicMeeting", 1.5);
        weights.put("financialNegotiation", 1.5);
        weights.put("financialTransaction", 1.2);
        return weights;
    }

    public TemporalProximity getTemporalProximity() {
        return temporalProximity;
    }

    public Financial getFinancial() {
        return financial;
    }

    public Map<String, Double> getInteractionWeights() {
        return interactionWeights;
    }

    public void setInteractionWeights(Map<String, Double> interactionWeights) {
        this.interactionWeights = interactionWeights;
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    public void setDefaultWeight(double defaultWeight) {
        this.defaultWeight = defaultWeight;
    }

    /** How interaction history turns into distance. */
    public static class TemporalProximity {

        /** Length of one decay period. The model is written in days. */
        private int periodHours = 24;

        /** What one period of silence costs, as a factor between 0 and 1. */
        private double decayFactor = 0.9;

        /** Keeps a person with no interactions at all from dividing by zero. */
        private double epsilon = 0.01;

        /** Distance of the closest person on screen. */
        private double minDistance = 80;

        /** Distance of the farthest. */
        private double maxDistance = 600;

        public int getPeriodHours() {
            return periodHours;
        }

        public void setPeriodHours(int periodHours) {
            this.periodHours = periodHours;
        }

        public double getDecayFactor() {
            return decayFactor;
        }

        public void setDecayFactor(double decayFactor) {
            this.decayFactor = decayFactor;
        }

        public double getEpsilon() {
            return epsilon;
        }

        public void setEpsilon(double epsilon) {
            this.epsilon = epsilon;
        }

        public double getMinDistance() {
            return minDistance;
        }

        public void setMinDistance(double minDistance) {
            this.minDistance = minDistance;
        }

        public double getMaxDistance() {
            return maxDistance;
        }

        public void setMaxDistance(double maxDistance) {
            this.maxDistance = maxDistance;
        }
    }

    /** How money turns into node size. */
    public static class Financial {

        /**
         * The currency the headline figures are stated in. Amounts in any other
         * currency are reported separately rather than converted: this system
         * has no exchange rates and inventing them would be a lie with a
         * decimal point on it.
         */
        private String baseCurrency = "BRL";

        /** Radius of a node with no money behind it. */
        private double minRadius = 8;

        /** Radius nothing may exceed, however large the sums. */
        private double maxRadius = 40;

        /**
         * The visual scaling constant of the logarithmic mapping.
         *
         * <p>Chosen so that the amounts a person actually deals in spread across
         * the band instead of all pressing against the ceiling: at 2.0, a few
         * thousand and a few hundred thousand are visibly different sizes, and it
         * takes a truly extreme sum to reach the maximum.
         */
        private double alpha = 2.0;

        public String getBaseCurrency() {
            return baseCurrency;
        }

        public void setBaseCurrency(String baseCurrency) {
            this.baseCurrency = baseCurrency;
        }

        public double getMinRadius() {
            return minRadius;
        }

        public void setMinRadius(double minRadius) {
            this.minRadius = minRadius;
        }

        public double getMaxRadius() {
            return maxRadius;
        }

        public void setMaxRadius(double maxRadius) {
            this.maxRadius = maxRadius;
        }

        public double getAlpha() {
            return alpha;
        }

        public void setAlpha(double alpha) {
            this.alpha = alpha;
        }
    }
}
