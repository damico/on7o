package org.on7o.server.reconcile;

/**
 * The outcome of merging a consolidated thought into the HCIN.
 */
public enum ReconciliationStatus {

    /** The thought was merged. It may have added nothing, if it had all been merged before. */
    RECONCILED,

    /** The thought held nothing that could be merged. */
    NOTHING_TO_MERGE
}
