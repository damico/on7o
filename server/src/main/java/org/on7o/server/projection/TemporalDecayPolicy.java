package org.on7o.server.projection;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * How much an interaction still counts, given how long ago it was.
 *
 * <p>The model is one factor per elapsed period: with a daily factor of 0.9, an
 * interaction is worth 0.9 of itself after a day, 0.48 after a week and 0.04
 * after a month. Repetition is what holds someone close, which is exactly the
 * intended reading.
 */
@Service
public class TemporalDecayPolicy {

    private final ProjectionProperties properties;

    public TemporalDecayPolicy(ProjectionProperties properties) {
        this.properties = properties;
    }

    /**
     * How many whole periods separate an event from the moment being asked
     * about.
     *
     * <p>Anything in the future of {@code asOf}, and anything within the current
     * period, counts as zero: today is today.
     */
    public long periodsBetween(Instant occurredAt, Instant asOf) {
        long hours = Duration.between(occurredAt, asOf).toHours();
        if (hours <= 0) {
            return 0;
        }
        return hours / properties.getTemporalProximity().getPeriodHours();
    }

    /** The surviving fraction of an interaction that happened this many periods ago. */
    public double decayOver(long periods) {
        return Math.pow(properties.getTemporalProximity().getDecayFactor(), Math.max(0, periods));
    }
}
