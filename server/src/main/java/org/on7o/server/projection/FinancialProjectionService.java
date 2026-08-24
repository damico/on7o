package org.on7o.server.projection;

import org.on7o.server.hcin.HcinEntity;
import org.on7o.server.hcin.HcinMembership;
import org.on7o.server.hcin.HcinRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the ego-centric financial projection.
 *
 * <p>Everything it needs has already been measured; this is where measurements
 * become something worth drawing. It decides which entities are on the picture,
 * what encloses them, and what each edge says about money.
 *
 * <p>It stops short of layout. Nothing here produces coordinates: a node gets a
 * distance from the centre and a size, and where that puts it on a screen is the
 * renderer's problem. Keeping the line there is what lets the same projection be
 * drawn as a graph, a table, or anything else.
 */
@Service
public class FinancialProjectionService {

    private final HcinRepository repository;
    private final RelationshipMetricsService metrics;
    private final ProjectionProperties properties;

    public FinancialProjectionService(HcinRepository repository,
                                      RelationshipMetricsService metrics,
                                      ProjectionProperties properties) {
        this.repository = repository;
        this.metrics = metrics;
        this.properties = properties;
    }

    /**
     * The projection as of an instant.
     *
     * @param asOf the moment being drawn; nothing later exists yet
     */
    public GraphProjection project(Instant asOf) {
        String ego = repository.ego();
        Map<String, RelationshipMetrics> measured = metrics.metrics(asOf);
        Map<String, String> labels = labels();
        Map<String, List<String>> membershipsByPerson = membershipsByPerson(asOf);
        Set<String> organizations = organizationUris();

        List<ProjectionNode> nodes = new ArrayList<>();
        List<ProjectionEdge> edges = new ArrayList<>();

        nodes.add(egoNode(ego, labels, membershipsByPerson));

        for (Map.Entry<String, RelationshipMetrics> entry : sorted(measured)) {
            String uri = entry.getKey();
            RelationshipMetrics entity = entry.getValue();

            if (!isWorthDrawing(uri, entity, organizations, membershipsByPerson)) {
                continue;
            }

            nodes.add(node(uri, entity, labels, membershipsByPerson, organizations));
            edges.add(edge(ego, uri, entity));
        }

        return new GraphProjection(asOf, ego, nodes, edges,
                groups(ego, organizations, membershipsByPerson, labels, nodes, measured));
    }

    /**
     * Whether an entity belongs on the picture, at the instant being drawn.
     *
     * <p>People are drawn whether or not money ever moved: someone the ego meets
     * weekly and never pays is a real relationship, and someone who belongs to an
     * organization the ego deals with is part of the picture even before they
     * have ever spoken.
     *
     * <p>What is left out is someone the ego had no connection to yet. A person
     * first met in May does not belong in a projection of March, and drawing them
     * there would let a later fact change an earlier picture.
     *
     * <p>Organizations are normally the setting rather than a participant, so one
     * becomes a node of its own only when the ego deals with it directly.
     */
    private boolean isWorthDrawing(String uri,
                                   RelationshipMetrics entity,
                                   Set<String> organizations,
                                   Map<String, List<String>> membershipsByPerson) {

        boolean connected = !entity.magnitude().isEmpty()
                || entity.proximity().proximity() > 0
                || entity.authority().isHeld();

        if (organizations.contains(uri)) {
            return connected;
        }
        return connected || membershipsByPerson.containsKey(uri);
    }

    private ProjectionNode egoNode(String ego,
                                   Map<String, String> labels,
                                   Map<String, List<String>> membershipsByPerson) {
        return new ProjectionNode(
                ego,
                labels.getOrDefault(ego, "Me"),
                NodeType.EGO,
                membershipsByPerson.getOrDefault(ego, List.of()),
                0,
                0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                properties.getFinancial().getBaseCurrency(),
                properties.getFinancial().getMinRadius(),
                FinancialAuthorityState.NONE,
                0);
    }

    private ProjectionNode node(String uri,
                                RelationshipMetrics entity,
                                Map<String, String> labels,
                                Map<String, List<String>> membershipsByPerson,
                                Set<String> organizations) {

        FinancialMagnitude money = entity.magnitude();

        return new ProjectionNode(
                uri,
                labels.getOrDefault(uri, uri),
                organizations.contains(uri) ? NodeType.ORGANIZATION : NodeType.PERSON,
                membershipsByPerson.getOrDefault(uri, List.of()),
                entity.proximity().proximity(),
                entity.visualDistance(),
                money.gross(),
                money.inflow(),
                money.outflow(),
                money.net(),
                money.currency(),
                entity.radius(),
                entity.authority(),
                entity.proximity().contributions().size());
    }

    /**
     * The ego's edge to one entity.
     *
     * <p>An edge exists for every relationship, with or without money. The
     * absence of a direct flow is itself worth seeing, which is what the dashed
     * style is for.
     */
    private ProjectionEdge edge(String ego, String uri, RelationshipMetrics entity) {
        FinancialMagnitude money = entity.magnitude();
        boolean direct = !money.isEmpty();

        return new ProjectionEdge(
                ego,
                uri,
                direct,
                summarize(money),
                direct ? FlowStyle.SOLID : FlowStyle.DASHED,
                money.gross(),
                money.currency());
    }

    private static FlowSummary summarize(FinancialMagnitude money) {
        boolean in = money.inflow().signum() > 0;
        boolean out = money.outflow().signum() > 0;

        if (in && out) {
            return FlowSummary.BOTH;
        }
        if (in) {
            return FlowSummary.INFLOW;
        }
        return out ? FlowSummary.OUTFLOW : FlowSummary.NONE;
    }

    /** One group per organization that actually has someone in it on this picture. */
    private List<ProjectionGroup> groups(String ego,
                                         Set<String> organizations,
                                         Map<String, List<String>> membershipsByPerson,
                                         Map<String, String> labels,
                                         List<ProjectionNode> nodes,
                                         Map<String, RelationshipMetrics> measured) {

        Set<String> drawn = new LinkedHashSet<>();
        nodes.forEach(node -> drawn.add(node.id()));

        Map<String, List<String>> members = new LinkedHashMap<>();
        membershipsByPerson.forEach((person, orgs) -> {
            if (!drawn.contains(person)) {
                return;
            }
            orgs.forEach(org -> members.computeIfAbsent(org, key -> new ArrayList<>()).add(person));
        });

        List<ProjectionGroup> groups = new ArrayList<>();
        for (String organization : organizations) {
            List<String> inside = members.getOrDefault(organization, List.of());
            if (!inside.isEmpty()) {
                groups.add(group(ego, organization, labels, inside, measured));
            }
        }
        return groups;
    }

    /**
     * One organization, with the ego's financial standing towards everyone
     * inside it.
     *
     * <p>The organization itself counts alongside its people: a payment to ACME
     * is money leaving towards ACME whether it went to the company or to someone
     * who works there. And when the group is the ego's own organization, the
     * ego's entire position is what it stands for, which is why it takes the
     * colour of whether they are, on balance, paying or being paid.
     */
    private ProjectionGroup group(String ego,
                                  String organization,
                                  Map<String, String> labels,
                                  List<String> inside,
                                  Map<String, RelationshipMetrics> measured) {

        BigDecimal inflow = BigDecimal.ZERO;
        BigDecimal outflow = BigDecimal.ZERO;
        String currency = properties.getFinancial().getBaseCurrency();

        Set<String> counted = new LinkedHashSet<>(inside);
        counted.add(organization);

        for (String member : counted) {
            if (member.equals(ego)) {
                for (RelationshipMetrics everyone : measured.values()) {
                    inflow = inflow.add(everyone.magnitude().inflow());
                    outflow = outflow.add(everyone.magnitude().outflow());
                }
                continue;
            }
            RelationshipMetrics metrics = measured.get(member);
            if (metrics != null) {
                inflow = inflow.add(metrics.magnitude().inflow());
                outflow = outflow.add(metrics.magnitude().outflow());
                currency = metrics.magnitude().currency();
            }
        }

        BigDecimal net = inflow.subtract(outflow);
        boolean moved = inflow.signum() > 0 || outflow.signum() > 0;

        FlowSummary direction = !moved ? FlowSummary.NONE
                : net.signum() > 0 ? FlowSummary.INFLOW
                : net.signum() < 0 ? FlowSummary.OUTFLOW
                : FlowSummary.BOTH;

        return new ProjectionGroup(
                organization,
                labels.getOrDefault(organization, organization),
                inside,
                direction,
                moved ? FlowStyle.SOLID : FlowStyle.DASHED,
                net,
                currency);
    }

    /** Who belongs where, as of the instant being drawn. Someone may belong to several places. */
    private Map<String, List<String>> membershipsByPerson(Instant asOf) {
        Map<String, List<String>> byPerson = new LinkedHashMap<>();
        for (HcinMembership membership : repository.memberships(asOf)) {
            byPerson.computeIfAbsent(membership.personUri(), person -> new ArrayList<>())
                    .add(membership.organizationUri());
        }
        return byPerson;
    }

    private Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        repository.people().forEach(entity -> putLabel(labels, entity));
        repository.organizations().forEach(entity -> putLabel(labels, entity));
        return labels;
    }

    private static void putLabel(Map<String, String> labels, HcinEntity entity) {
        if (entity.label() != null) {
            labels.putIfAbsent(entity.uri(), entity.label());
        }
    }

    private Set<String> organizationUris() {
        Set<String> uris = new LinkedHashSet<>();
        repository.organizations().forEach(entity -> uris.add(entity.uri()));
        return uris;
    }

    /** Closest first, so that a reader of the raw JSON sees the same order as the picture. */
    private static List<Map.Entry<String, RelationshipMetrics>> sorted(Map<String, RelationshipMetrics> measured) {
        return measured.entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> entry.getValue().visualDistance()))
                .toList();
    }
}
