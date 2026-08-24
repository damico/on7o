package org.on7o.server.projection;

import org.on7o.server.hcin.AuthorityType;

import java.util.Set;

/**
 * What financial decision power someone holds, as the projection reads it.
 *
 * <p>Deliberately separate from flow: a person who approves payments they never
 * receive holds authority and moves no money, and a person who receives payments
 * they have no say over is the reverse.
 */
public enum FinancialAuthorityState {

    /** No relevant decision power the network knows of. */
    NONE,

    /** Power over money going out. */
    EXPENDITURE,

    /** Power over money coming in. */
    REVENUE,

    /** Both. */
    MIXED;

    /** The state implied by the authority types someone holds. */
    public static FinancialAuthorityState of(Set<AuthorityType> types) {
        boolean expenditure = types.contains(AuthorityType.EXPENDITURE);
        boolean revenue = types.contains(AuthorityType.REVENUE);

        if (expenditure && revenue) {
            return MIXED;
        }
        if (expenditure) {
            return EXPENDITURE;
        }
        return revenue ? REVENUE : NONE;
    }

    /** True when the network knows of any relevant authority at all. */
    public boolean isHeld() {
        return this != NONE;
    }
}
