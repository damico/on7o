package org.on7o.server.ontology;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns a Turtle (including RDF-star) ontology stage into an {@link OntologyDiagram}
 * of nodes and edges the browser can lay out and draw as SVG.
 *
 * <p>Resource and blank nodes become {@link DiagramNode}s, typed as "class",
 * "property", "individual" or "vocabulary" from their {@code rdf:type}. Object
 * properties become {@link DiagramEdge}s; so does an individual's {@code rdf:type}
 * link to its class, drawn as a "type" edge, so the class gets its own node even
 * when nothing else points to it. A resource's own {@code rdf:type} declaration
 * as {@code owl:Class}/{@code owl:ObjectProperty}/{@code owl:DatatypeProperty} is
 * metadata about that resource, not a domain relation, and stays off the diagram.
 * Literal-valued triples (comments, labels, confidence, ...) are folded into the
 * tooltip of the node or edge they describe instead of becoming diagram elements
 * of their own, since a raw literal is not something a reader needs to pan or
 * zoom to.
 */
@Service
public class TurtleDiagramParser {

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";
    private static final String OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty";
    private static final String OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty";
    private static final Set<String> LABEL_PREDICATES = Set.of(
            "http://www.w3.org/2000/01/rdf-schema#comment",
            "http://www.w3.org/2000/01/rdf-schema#label");

    /** Properties that give something the name a person would call it by. */
    private static final Set<String> NAME_PREDICATES = Set.of(
            "http://www.w3.org/2000/01/rdf-schema#label",
            "http://on7o.io/hcin#label",
            "http://xmlns.com/foaf/0.1/name",
            "http://www.w3.org/2004/02/skos/core#prefLabel");

    /** How many of the things an anonymous node points at fit in its name. */
    private static final int ENDS_SHOWN = 2;

    /**
     * Parses Turtle text into a diagram. Malformed input yields an empty diagram
     * rather than an exception: a broken LLM response should not crash the viewer.
     */
    public OntologyDiagram parse(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        try {
            RDFParser.fromString(turtle).lang(Lang.TURTLE).parse(model);
        } catch (RuntimeException e) {
            return new OntologyDiagram(List.of(), List.of());
        }

        Graph graph = model.getGraph();
        List<Triple> all = graph.find(Node.ANY, Node.ANY, Node.ANY).toList();

        List<Triple> base = new ArrayList<>();
        List<Triple> annotations = new ArrayList<>();
        for (Triple t : all) {
            if (t.getSubject().isNodeTriple()) {
                annotations.add(t);
            } else {
                base.add(t);
            }
        }

        Set<Node> classNodes = new HashSet<>();
        Set<Node> propertyNodes = new HashSet<>();
        Set<Node> individualNodes = new HashSet<>();
        for (Triple t : base) {
            if (!t.getPredicate().getURI().equals(RDF_TYPE)) {
                continue;
            }
            String typeUri = t.getObject().isURI() ? t.getObject().getURI() : "";
            if (typeUri.equals(OWL_CLASS)) {
                classNodes.add(t.getSubject());
            } else if (typeUri.equals(OWL_OBJECT_PROPERTY) || typeUri.equals(OWL_DATATYPE_PROPERTY)) {
                propertyNodes.add(t.getSubject());
            } else {
                individualNodes.add(t.getSubject());
            }
        }

        Map<String, String> tooltips = new HashMap<>();
        for (Triple t : base) {
            if (!t.getObject().isLiteral()) {
                continue;
            }
            String pred = t.getPredicate().getURI();
            if (!LABEL_PREDICATES.contains(pred) && !pred.equals(RDF_TYPE)) {
                continue;
            }
            String key = model.shortForm(t.getSubject().isURI() ? t.getSubject().getURI() : blankId(t.getSubject()));
            String note = localName(pred) + ": " + t.getObject().getLiteralLexicalForm();
            tooltips.merge(key, note, (a, b) -> a + "\n" + b);
        }

        Map<String, DiagramNode> nodes = new LinkedHashMap<>();
        Map<String, DiagramEdge> edgesByKey = new LinkedHashMap<>();
        int edgeSeq = 0;

        for (Triple t : base) {
            Node predicate = t.getPredicate();
            if (t.getObject().isLiteral()) {
                continue;
            }
            boolean isType = predicate.getURI().equals(RDF_TYPE);
            if (isType) {
                String typeUri = t.getObject().isURI() ? t.getObject().getURI() : "";
                if (typeUri.equals(OWL_CLASS) || typeUri.equals(OWL_OBJECT_PROPERTY) || typeUri.equals(OWL_DATATYPE_PROPERTY)) {
                    continue;
                }
            }

            String sourceId = nodeId(model, t.getSubject());
            String targetId = nodeId(model, t.getObject());
            nodes.computeIfAbsent(sourceId, id -> toNode(model, t.getSubject(), classNodes, propertyNodes, individualNodes, tooltips));
            nodes.computeIfAbsent(targetId, id -> toNode(model, t.getObject(), classNodes, propertyNodes, individualNodes, tooltips));

            String predLocal = isType ? "type" : localName(predicate.getURI());
            String edgeKey = sourceId + "|" + predLocal + "|" + targetId;
            edgesByKey.putIfAbsent(edgeKey,
                    new DiagramEdge("e" + (edgeSeq++), sourceId, targetId, predLocal, null));
        }

        for (Triple t : annotations) {
            Triple inner = t.getSubject().getTriple();
            if (inner.getObject().isLiteral() && !inner.getSubject().isURI() && !inner.getSubject().isBlank()) {
                continue;
            }
            String sourceId = nodeId(model, inner.getSubject());
            String targetId = inner.getObject().isLiteral() ? null : nodeId(model, inner.getObject());
            String predLocal = localName(inner.getPredicate().getURI());
            String note = localName(t.getPredicate().getURI()) + "="
                    + (t.getObject().isLiteral() ? t.getObject().getLiteralLexicalForm() : nodeId(model, t.getObject()));

            String edgeKey = sourceId + "|" + predLocal + "|" + targetId;
            DiagramEdge existing = edgesByKey.get(edgeKey);
            if (existing != null) {
                String tooltip = existing.tooltip() == null ? note : existing.tooltip() + "\n" + note;
                edgesByKey.put(edgeKey, new DiagramEdge(existing.id(), existing.sourceId(), existing.targetId(), existing.label(), tooltip));
            } else if (nodes.containsKey(sourceId)) {
                DiagramNode existingNode = nodes.get(sourceId);
                String tooltip = existingNode.tooltip() == null ? note : existingNode.tooltip() + "\n" + note;
                nodes.put(sourceId, new DiagramNode(existingNode.id(), existingNode.label(), existingNode.type(), tooltip));
            }
        }

        return new OntologyDiagram(List.copyOf(nodes.values()), List.copyOf(edgesByKey.values()));
    }

    private DiagramNode toNode(Model model, Node n, Set<Node> classNodes, Set<Node> propertyNodes,
                                Set<Node> individualNodes, Map<String, String> tooltips) {
        String id = nodeId(model, n);
        String label = shortLabel(model, n);
        String type;
        if (classNodes.contains(n)) {
            type = "class";
        } else if (propertyNodes.contains(n)) {
            type = "property";
        } else if (individualNodes.contains(n)) {
            type = "individual";
        } else {
            type = "vocabulary";
        }
        return new DiagramNode(id, label, type, tooltips.get(model.shortForm(n.isURI() ? n.getURI() : blankId(n))));
    }

    private String nodeId(Model model, Node n) {
        String raw = n.isURI() ? model.shortForm(n.getURI()) : blankId(n);
        return raw.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String shortLabel(Model model, Node n) {
        if (n.isBlank()) {
            return anonymousLabel(model, n);
        }
        String qname = model.shortForm(n.getURI());
        int colon = qname.indexOf(':');
        return colon >= 0 ? qname.substring(colon + 1) : qname;
    }

    /**
     * A node the thought left anonymous, named by what it says.
     *
     * <p>A membership written the way the W3C organization vocabulary recommends
     * has no name of its own, and the label a parser invents for it is a hex
     * string that means nothing to anybody. What the node is and who it joins are
     * right there in its own statements, and that is a name a reader can use:
     * "Membership: Ninoska / Expo Teleinfo" rather than 5d641ce11553.
     *
     * <p>The hex label survives only as a last resort, for a node that says
     * nothing at all.
     */
    private String anonymousLabel(Model model, Node n) {
        String type = null;
        Map<String, String> ends = new TreeMap<>();

        for (Triple t : model.getGraph().find(n, Node.ANY, Node.ANY).toList()) {
            if (!t.getObject().isURI()) {
                continue;
            }
            if (t.getPredicate().getURI().equals(RDF_TYPE)) {
                type = shortLabel(model, t.getObject());
            } else {
                // Ordered by the property, so the name is the same on every read
                // and reads the way the model does: a member before what they are
                // a member of. The separator sorts below every character a URI can
                // hold, so "member" really does come before "memberOf".
                ends.put(t.getPredicate().getURI() + '\u0000' + t.getObject().getURI(),
                        nameOf(model, t.getObject()));
            }
        }

        String joined = ends.values().stream().limit(ENDS_SHOWN)
                .collect(java.util.stream.Collectors.joining(" / "));
        if (type == null) {
            return joined.isEmpty() ? blankId(n) : joined;
        }
        return joined.isEmpty() ? type : type + ": " + joined;
    }

    /** What the thought calls a node, when it says, and its short form when it does not. */
    private String nameOf(Model model, Node n) {
        for (Triple t : model.getGraph().find(n, Node.ANY, Node.ANY).toList()) {
            if (t.getObject().isLiteral() && NAME_PREDICATES.contains(t.getPredicate().getURI())) {
                return t.getObject().getLiteralLexicalForm();
            }
        }
        return shortLabel(model, n);
    }

    private String blankId(Node n) {
        return "_:" + n.getBlankNodeLabel();
    }

    private String localName(String uri) {
        int cut = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
        return cut >= 0 ? uri.substring(cut + 1) : uri;
    }
}
