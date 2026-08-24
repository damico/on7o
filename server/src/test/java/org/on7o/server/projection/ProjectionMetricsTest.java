package org.on7o.server.projection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.on7o.server.hcin.HcinFixture;
import org.on7o.server.hcin.HcinRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The relational and financial measurements.
 *
 * <p>Every assertion is made against a fixed instant. Nothing here reads the
 * clock, so these tests say the same thing whenever they are run, which is the
 * same property the projection itself needs in order to be able to answer for
 * last year.
 */
class ProjectionMetricsTest {

    private static final Instant NOW = Instant.parse("2026-08-24T15:00:00Z");

    private static final String WEEKLY_AND_STALE = """
            me:me    a hcin:Person ; hcin:label "Me" .
            me:bob   a hcin:Person ; hcin:label "Bob" .
            me:sam   a hcin:Person ; hcin:label "Sam" .

            ev:bob-1 a hcin:Interaction ; hcin:participant me:me , me:bob ;
                hcin:interactionType "meeting" ;
                hcin:occurredAt "2026-08-24T12:00:00.000Z"^^xsd:dateTime .
            ev:bob-2 a hcin:Interaction ; hcin:participant me:me , me:bob ;
                hcin:interactionType "meeting" ;
                hcin:occurredAt "2026-08-17T12:00:00.000Z"^^xsd:dateTime .
            ev:bob-3 a hcin:Interaction ; hcin:participant me:me , me:bob ;
                hcin:interactionType "meeting" ;
                hcin:occurredAt "2026-08-10T12:00:00.000Z"^^xsd:dateTime .

            ev:sam-1 a hcin:Interaction ; hcin:participant me:me , me:sam ;
                hcin:interactionType "meeting" ;
                hcin:occurredAt "2026-05-24T12:00:00.000Z"^^xsd:dateTime .
            """;

    private HcinFixture hcin;
    private HcinRepository repository;
    private ProjectionProperties properties;
    private InteractionProximityService proximity;
    private VisualDistanceNormalizer distances;
    private FinancialMetricsService financial;
    private NodeRadiusScale radius;
    private RelationshipMetricsService metrics;

    @BeforeEach
    void setUp() throws IOException {
        hcin = new HcinFixture();
        repository = hcin.repository();
        properties = new ProjectionProperties();

        InteractionWeightPolicy weights = new InteractionWeightPolicy(properties);
        TemporalDecayPolicy decay = new TemporalDecayPolicy(properties);

        proximity = new InteractionProximityService(repository, weights, decay);
        distances = new VisualDistanceNormalizer(properties);
        financial = new FinancialMetricsService(repository, properties);
        radius = new NodeRadiusScale(properties);
        metrics = new RelationshipMetricsService(repository, proximity, distances, financial, radius, properties);
    }

    // -------------------------------------------------------------------------
    // Proximity
    // -------------------------------------------------------------------------

    @Test
    void weeklyContactBeatsOneMeetingAQuarterAgo() {
        hcin.loadAsserted(WEEKLY_AND_STALE);

        Map<String, ProximityScore> scores = proximity.scores(NOW);

        assertThat(scores.get("urn:hcin:person:bob").proximity())
                .isGreaterThan(scores.get("urn:hcin:person:sam").proximity());
    }

    @Test
    void inactivityPullsSomeoneAway() {
        hcin.loadAsserted(WEEKLY_AND_STALE);

        double today = proximity.scoreOf("urn:hcin:person:bob", NOW).proximity();
        double inAMonth = proximity.scoreOf("urn:hcin:person:bob", NOW.plus(java.time.Duration.ofDays(30)))
                .proximity();

        assertThat(inAMonth).isLessThan(today);
    }

    @Test
    void aRecentMeetingPullsSomeoneBackIn() {
        hcin.loadAsserted(WEEKLY_AND_STALE);
        double before = proximity.scoreOf("urn:hcin:person:sam", NOW).proximity();

        hcin.loadAsserted("""
                ev:sam-2 a hcin:Interaction ; hcin:participant me:me , me:sam ;
                    hcin:interactionType "meeting" ;
                    hcin:occurredAt "2026-08-24T09:00:00.000Z"^^xsd:dateTime .
                """);

        assertThat(proximity.scoreOf("urn:hcin:person:sam", NOW).proximity()).isGreaterThan(before);
    }

    @Test
    void interactionsAfterTheInstantBeingAskedAboutDoNotCount() {
        hcin.loadAsserted(WEEKLY_AND_STALE);

        Instant beforeTheLunches = Instant.parse("2026-08-11T00:00:00Z");
        ProximityScore score = proximity.scoreOf("urn:hcin:person:bob", beforeTheLunches);

        assertThat(score.contributions()).hasSize(1);
        assertThat(score.contributions().get(0).occurredAt())
                .isEqualTo(Instant.parse("2026-08-10T12:00:00Z"));
    }

    @Test
    void explainsHowItArrivedAtAScore() {
        hcin.loadAsserted(WEEKLY_AND_STALE);

        ProximityScore score = proximity.scoreOf("urn:hcin:person:bob", NOW);

        assertThat(score.contributions()).hasSize(3);
        assertThat(score.contributions().get(0).type()).isEqualTo("meeting");
        assertThat(score.contributions().get(0).weight()).isEqualTo(1.0);
        assertThat(score.contributions().get(0).periodsElapsed()).isZero();
        assertThat(score.contributions().get(0).decay()).isEqualTo(1.0);

        double summed = score.contributions().stream()
                .mapToDouble(ProximityContribution::contribution).sum();
        assertThat(score.proximity()).isEqualTo(summed);
    }

    @Test
    void weighsAStrategicMeetingAboveAPassingMention() {
        hcin.loadAsserted("""
                me:me    a hcin:Person ; hcin:label "Me" .
                me:carol a hcin:Person ; hcin:label "Carol" .
                me:dave  a hcin:Person ; hcin:label "Dave" .

                ev:carol a hcin:Interaction ; hcin:participant me:me , me:carol ;
                    hcin:interactionType "strategicMeeting" ;
                    hcin:occurredAt "2026-08-24T12:00:00.000Z"^^xsd:dateTime .
                ev:dave a hcin:Interaction ; hcin:participant me:me , me:dave ;
                    hcin:interactionType "mention" ;
                    hcin:occurredAt "2026-08-24T12:00:00.000Z"^^xsd:dateTime .
                """);

        Map<String, ProximityScore> scores = proximity.scores(NOW);
        assertThat(scores.get("urn:hcin:person:carol").proximity())
                .isGreaterThan(scores.get("urn:hcin:person:dave").proximity());
    }

    // -------------------------------------------------------------------------
    // Visual distance
    // -------------------------------------------------------------------------

    @Test
    void turnsProximityIntoDistanceInsideTheConfiguredBand() {
        hcin.loadAsserted(WEEKLY_AND_STALE);

        Map<String, Double> visual = distances.distancesFor(proximity.scores(NOW));

        assertThat(visual.get("urn:hcin:person:bob"))
                .isEqualTo(properties.getTemporalProximity().getMinDistance());
        assertThat(visual.get("urn:hcin:person:sam"))
                .isEqualTo(properties.getTemporalProximity().getMaxDistance());
        assertThat(visual.values()).allSatisfy(distance -> assertThat(distance)
                .isBetween(properties.getTemporalProximity().getMinDistance(),
                        properties.getTemporalProximity().getMaxDistance()));
    }

    @Test
    void keepsSomeoneNeverMetOffTheChartWithoutDividingByZero() {
        assertThat(distances.rawDistance(0.0)).isFinite().isPositive();
    }

    // -------------------------------------------------------------------------
    // Money
    // -------------------------------------------------------------------------

    @Test
    void separatesVolumeFromBalance() {
        hcin.loadAsserted("""
                me:me    a hcin:Person ; hcin:label "Me" .
                org:acme a hcin:Organization ; hcin:label "ACME" .

                ev:in a hcinf:FinancialFlow ;
                    hcinf:flowSource org:acme ; hcinf:flowTarget me:me ;
                    hcinf:direction hcinf:Inflow ;
                    hcinf:amount "1000000.00"^^xsd:decimal ; hcinf:currency "BRL" ;
                    hcin:occurredAt "2026-08-01T10:00:00.000Z"^^xsd:dateTime .

                ev:out a hcinf:FinancialFlow ;
                    hcinf:flowSource me:me ; hcinf:flowTarget org:acme ;
                    hcinf:direction hcinf:Outflow ;
                    hcinf:amount "950000.00"^^xsd:decimal ; hcinf:currency "BRL" ;
                    hcin:occurredAt "2026-08-02T10:00:00.000Z"^^xsd:dateTime .
                """);

        FinancialMagnitude magnitude = financial.magnitudeOf("urn:hcin:org:acme", NOW);

        assertThat(magnitude.gross()).isEqualByComparingTo("1950000.00");
        assertThat(magnitude.net()).isEqualByComparingTo("50000.00");
        assertThat(magnitude.inflow()).isEqualByComparingTo("1000000.00");
        assertThat(magnitude.outflow()).isEqualByComparingTo("950000.00");
        assertThat(magnitude.currency()).isEqualTo("BRL");
    }

    @Test
    void refusesToInventAnExchangeRate() {
        hcin.loadAsserted("""
                me:me    a hcin:Person ; hcin:label "Me" .
                org:acme a hcin:Organization ; hcin:label "ACME" .

                ev:brl a hcinf:FinancialFlow ;
                    hcinf:flowSource me:me ; hcinf:flowTarget org:acme ;
                    hcinf:direction hcinf:Outflow ;
                    hcinf:amount "1000.00"^^xsd:decimal ; hcinf:currency "BRL" ;
                    hcin:occurredAt "2026-08-01T10:00:00.000Z"^^xsd:dateTime .

                ev:usd a hcinf:FinancialFlow ;
                    hcinf:flowSource me:me ; hcinf:flowTarget org:acme ;
                    hcinf:direction hcinf:Outflow ;
                    hcinf:amount "500.00"^^xsd:decimal ; hcinf:currency "USD" ;
                    hcin:occurredAt "2026-08-02T10:00:00.000Z"^^xsd:dateTime .
                """);

        FinancialMagnitude magnitude = financial.magnitudeOf("urn:hcin:org:acme", NOW);

        assertThat(magnitude.gross()).isEqualByComparingTo("1000.00");
        assertThat(magnitude.unconverted()).containsExactly("USD");
        assertThat(magnitude.byCurrency()).containsKeys("BRL", "USD");
    }

    @Test
    void countsNoMoneyThatHadNotMovedYet() {
        hcin.loadAsserted("""
                me:me    a hcin:Person ; hcin:label "Me" .
                org:acme a hcin:Organization ; hcin:label "ACME" .

                ev:later a hcinf:FinancialFlow ;
                    hcinf:flowSource me:me ; hcinf:flowTarget org:acme ;
                    hcinf:direction hcinf:Outflow ;
                    hcinf:amount "1000.00"^^xsd:decimal ; hcinf:currency "BRL" ;
                    hcin:occurredAt "2026-08-20T10:00:00.000Z"^^xsd:dateTime .
                """);

        assertThat(financial.magnitudeOf("urn:hcin:org:acme", Instant.parse("2026-08-01T00:00:00Z")).gross())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(financial.magnitudeOf("urn:hcin:org:acme", NOW).gross())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void keepsAuthoritySeparateFromFlow() {
        hcin.loadAsserted("""
                me:me    a hcin:Person ; hcin:label "Me" .
                me:maria a hcin:Person ; hcin:label "Maria" .
                org:abc  a hcin:Organization ; hcin:label "ABC" .

                ev:authority a hcinf:FinancialAuthority ;
                    hcinf:holder me:maria ; hcinf:organization org:abc ;
                    hcinf:authorityType hcinf:ExpenditureAuthority ;
                    hcin:validFrom "2026-01-01T00:00:00.000Z"^^xsd:dateTime .
                """);

        assertThat(financial.authorityStates(NOW))
                .containsEntry("urn:hcin:person:maria", FinancialAuthorityState.EXPENDITURE);
        assertThat(financial.magnitudeOf("urn:hcin:person:maria", NOW).isEmpty()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Node size
    // -------------------------------------------------------------------------

    @Test
    void growsNodesWithMoneyWithoutLettingOneSwallowThePicture() {
        double small = radius.radiusFor(new BigDecimal("1000"));
        double large = radius.radiusFor(new BigDecimal("1000000"));
        double absurd = radius.radiusFor(new BigDecimal("999999999999"));

        assertThat(small).isLessThan(large);
        assertThat(large).isLessThanOrEqualTo(properties.getFinancial().getMaxRadius());
        assertThat(absurd).isEqualTo(properties.getFinancial().getMaxRadius());
        assertThat(radius.radiusFor(BigDecimal.ZERO))
                .isEqualTo(properties.getFinancial().getMinRadius());
    }

    // -------------------------------------------------------------------------
    // The vector
    // -------------------------------------------------------------------------

    @Test
    void bringsEverythingTogetherIntoOneReadingPerPerson() {
        hcin.loadAsserted(WEEKLY_AND_STALE);
        hcin.loadAsserted("""
                ev:paid a hcinf:FinancialFlow ;
                    hcinf:flowSource me:me ; hcinf:flowTarget me:bob ;
                    hcinf:direction hcinf:Outflow ;
                    hcinf:amount "120000.00"^^xsd:decimal ; hcinf:currency "BRL" ;
                    hcin:occurredAt "2026-08-05T10:00:00.000Z"^^xsd:dateTime .
                """);

        Map<String, RelationshipMetrics> all = metrics.metrics(NOW);

        assertThat(all).doesNotContainKey(repository.ego());

        RelationshipMetrics bob = all.get("urn:hcin:person:bob");
        assertThat(bob.vector().interactionProximity()).isEqualTo(bob.proximity().proximity());
        assertThat(bob.vector().financialMagnitude()).isEqualByComparingTo("120000.00");
        assertThat(bob.vector().authority()).isZero();
        assertThat(bob.radius()).isGreaterThan(properties.getFinancial().getMinRadius());

        RelationshipMetrics sam = all.get("urn:hcin:person:sam");
        assertThat(sam.magnitude().isEmpty()).isTrue();
        assertThat(sam.radius()).isEqualTo(properties.getFinancial().getMinRadius());
        assertThat(sam.visualDistance()).isGreaterThan(bob.visualDistance());
    }

    @Test
    void saysNothingItHasNotComputed() {
        hcin.loadAsserted(WEEKLY_AND_STALE);

        RelationshipVector vector = metrics.metrics(NOW).get("urn:hcin:person:bob").vector();

        assertThat(vector.dependency()).isNull();
        assertThat(vector.reciprocity()).isNull();
    }
}
