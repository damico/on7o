package org.on7o.server.ontology;

/**
 * How much a knowledge artifact actually says.
 *
 * @param statements    triples in the artifact
 * @param entities      distinct individuals, that is subjects given a type of their own
 * @param relationships assertions linking one resource to another, type declarations aside
 */
public record KnowledgeMetrics(int statements, int entities, int relationships) {

    /** The metrics of an artifact that could not be parsed, or that says nothing. */
    public static KnowledgeMetrics empty() {
        return new KnowledgeMetrics(0, 0, 0);
    }
}
