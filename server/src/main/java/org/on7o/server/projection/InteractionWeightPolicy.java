package org.on7o.server.projection;

import org.on7o.server.hcin.HcinInteraction;
import org.springframework.stereotype.Service;

/**
 * What one interaction is worth.
 *
 * <p>A lunch and a passing mention are not the same evidence of closeness, and
 * the difference between them is an analytic parameter rather than a fact about
 * the world. Everything here comes from configuration for that reason.
 */
@Service
public class InteractionWeightPolicy {

    private final ProjectionProperties properties;

    public InteractionWeightPolicy(ProjectionProperties properties) {
        this.properties = properties;
    }

    /**
     * The weight of an interaction.
     *
     * <p>An interaction with no type counts as the cheapest thing it could be
     * rather than the average of what it might have been: assuming a meeting
     * because the type is missing would invent closeness out of ignorance.
     */
    public double weightOf(HcinInteraction interaction) {
        return weightOf(interaction.type());
    }

    /** The weight of an interaction type token. */
    public double weightOf(String type) {
        if (type == null || type.isBlank()) {
            return properties.getDefaultWeight();
        }
        return properties.getInteractionWeights()
                .getOrDefault(type.trim(), properties.getDefaultWeight());
    }
}
