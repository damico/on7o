package org.on7o.server.ontology;

import java.util.List;

/**
 * Nodes and edges extracted from one Turtle ontology stage (rThought, qThought or cThought),
 * ready to be laid out and drawn by the browser as SVG.
 */
public record OntologyDiagram(List<DiagramNode> nodes, List<DiagramEdge> edges) {}
