package org.on7o.server.ontology;

/**
 * A node in an ontology diagram.
 *
 * @param id      stable identifier, unique within the diagram
 * @param label   short display text
 * @param type    one of "class", "property", "individual", "datatype", "vocabulary"
 * @param tooltip extra detail shown on hover (source comments, literal values)
 */
public record DiagramNode(String id, String label, String type, String tooltip) {}
