package org.on7o.server.reconcile;

/**
 * Something a consolidated thought talks about, before it is known whether the
 * HCIN already knows it.
 *
 * @param localUri the URI it had inside the thought's own ontology
 * @param label    the name it was given there
 * @param kind     what sort of thing it appears to be
 */
public record CandidateEntity(String localUri, String label, EntityKind kind) {

    /** The label reduced to what entity matching compares. */
    public String matchKey() {
        return kind + "|" + HcinUris.normalize(label);
    }
}
