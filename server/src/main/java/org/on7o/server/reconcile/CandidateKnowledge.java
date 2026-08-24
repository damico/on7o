package org.on7o.server.reconcile;

import org.apache.jena.rdf.model.Model;

import java.util.List;

/**
 * Everything worth merging that could be read out of one consolidated thought.
 *
 * @param entities   the people, organizations and other things it names
 * @param statements the claims it makes between them
 * @param events     its HCIN-shaped nodes, kept as RDF because they carry more
 *                   structure than a triple: interactions, flows, authorities,
 *                   memberships and their properties
 */
public record CandidateKnowledge(
        List<CandidateEntity> entities,
        List<CandidateStatement> statements,
        Model events) {

    /** True when the thought said nothing that can be merged. */
    public boolean isEmpty() {
        return entities.isEmpty() && statements.isEmpty() && events.isEmpty();
    }
}
