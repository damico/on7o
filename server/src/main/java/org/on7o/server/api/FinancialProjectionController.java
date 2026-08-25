package org.on7o.server.api;

import org.on7o.server.hcin.HcinRepository;
import org.on7o.server.projection.FinancialProjectionService;
import org.on7o.server.projection.GraphProjection;
import org.on7o.server.projection.ProjectionProperties;
import org.on7o.server.projection.RelationshipMetrics;
import org.on7o.server.projection.RelationshipMetricsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * The financial projection, as data.
 *
 * <pre>
 * GET /api/hcin/financial-projection?asOf=2026-08-24T15:00:00Z
 * GET /api/hcin/financial-projection/metrics?asOf=2026-08-24T15:00:00Z
 * GET /api/hcin/projection-config
 * </pre>
 *
 * <p>Reading the clock happens here and nowhere else. Everything downstream is
 * given an explicit instant, which is what lets the same code answer for last
 * year and lets tests be about the model rather than about when they run.
 *
 * <p>The projection and its configuration are both plain JSON on purpose: saved
 * together they are enough to reproduce a picture exactly, which a screenshot
 * never is.
 */
@RestController
public class FinancialProjectionController {

    private final FinancialProjectionService projection;
    private final RelationshipMetricsService metrics;
    private final ProjectionProperties properties;
    private final HcinRepository repository;

    public FinancialProjectionController(FinancialProjectionService projection,
                                         RelationshipMetricsService metrics,
                                         ProjectionProperties properties,
                                         HcinRepository repository) {
        this.projection = projection;
        this.metrics = metrics;
        this.properties = properties;
        this.repository = repository;
    }

    /**
     * The graph to draw.
     *
     * @param asOf the instant to draw, ISO-8601; defaults to now
     */
    @GetMapping(value = "/api/hcin/financial-projection", produces = MediaType.APPLICATION_JSON_VALUE)
    public GraphProjection projection(@RequestParam(required = false) String asOf) {
        return projection.project(instantOf(asOf));
    }

    /**
     * The measurements behind it, closest first.
     *
     * @param asOf the instant to measure, ISO-8601; defaults to now
     */
    @GetMapping(value = "/api/hcin/financial-projection/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectionMetricsResponse metrics(@RequestParam(required = false) String asOf) {
        Instant instant = instantOf(asOf);

        List<RelationshipMetrics> measured = metrics.metrics(instant).values().stream()
                .sorted(Comparator.comparingDouble(RelationshipMetrics::visualDistance))
                .toList();

        return new ProjectionMetricsResponse(instant, repository.ego(), measured);
    }

    /**
     * What changed between two instants.
     *
     * <p>A snapshot says how things stand; only the difference says whether a
     * relationship is warming or cooling, whether money has shifted direction,
     * or whether authority has moved. Both ends are explicit, so the question
     * can be asked about any two dates, not only about the recent past.
     *
     * @param from the earlier instant, ISO-8601
     * @param to   the later instant, ISO-8601; defaults to now
     */
    @GetMapping(value = "/api/hcin/financial-projection/delta", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectionDeltaResponse delta(@RequestParam String from,
                                         @RequestParam(required = false) String to) {

        Instant earlier = instantOf(from);
        Instant later = instantOf(to);

        if (earlier.isAfter(later)) {
            throw new IllegalArgumentException("from must not be later than to");
        }

        return new ProjectionDeltaResponse(earlier, later, repository.ego(),
                metrics.deltas(earlier, later));
    }

    /**
     * The parameters the projection was computed with.
     *
     * <p>None of it is ontology: what a meeting is worth is a choice about how to
     * read the network. Saved beside a projection, it is what makes that
     * projection reproducible.
     */
    @GetMapping(value = "/api/hcin/projection-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectionProperties config() {
        return properties;
    }

    /**
     * Parses the instant asked about.
     *
     * <p>Accepts a plain instant or an offset date-time, since a caller writing
     * a timestamp by hand is likelier to write their own offset than UTC.
     */
    private static Instant instantOf(String asOf) {
        if (asOf == null || asOf.isBlank()) {
            return Instant.now();
        }
        try {
            String value = asOf.trim();
            return value.endsWith("Z") ? Instant.parse(value) : OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid asOf: " + asOf);
        }
    }
}
