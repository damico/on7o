package org.on7o.server.hcin;

/**
 * What a financial authority is power over.
 *
 * <p>Kept separate from flow: a person can approve payments they never receive,
 * and a person can receive payments they have no say over.
 */
public enum AuthorityType {

    /** Power over money going out. */
    EXPENDITURE,

    /** Power over money coming in. */
    REVENUE;

    /** Reads a type from its vocabulary URI, or null when it is not one of these. */
    public static AuthorityType of(String uri) {
        if (HcinVocabulary.EXPENDITURE_AUTHORITY.getURI().equals(uri)) {
            return EXPENDITURE;
        }
        if (HcinVocabulary.REVENUE_AUTHORITY.getURI().equals(uri)) {
            return REVENUE;
        }
        return null;
    }
}
