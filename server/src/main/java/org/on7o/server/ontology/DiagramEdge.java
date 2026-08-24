package org.on7o.server.ontology;

/**
 * A directed edge in an ontology diagram, connecting two {@link DiagramNode}s.
 *
 * @param id       stable identifier, unique within the diagram
 * @param sourceId id of the node the edge starts from
 * @param targetId id of the node the edge points to
 * @param label    predicate local name shown on the edge
 * @param tooltip  extra detail shown on hover (RDF-star annotations such as confidence)
 */
public record DiagramEdge(String id, String sourceId, String targetId, String label, String tooltip) {}
