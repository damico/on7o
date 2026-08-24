package org.on7o.server.hcin;

import java.util.List;

/**
 * What validating a piece of the HCIN produced.
 *
 * <p>{@code conforms} follows SHACL and is false whenever anything was reported.
 * It is deliberately not the same question as {@link #isUsable()}: data that
 * only raised clarification candidates does not conform, and is perfectly fine
 * to keep.
 *
 * @param conforms true when SHACL found nothing at all
 * @param findings everything found, most severe first
 */
public record ShaclReport(boolean conforms, List<ShaclFinding> findings) {

    /** A report over data that raised nothing. */
    public static ShaclReport clean() {
        return new ShaclReport(true, List.of());
    }

    /** Findings of one severity. */
    public List<ShaclFinding> of(ShaclSeverity severity) {
        return findings.stream().filter(finding -> finding.severity() == severity).toList();
    }

    /** True when nothing fatal was found, whatever else was. */
    public boolean isUsable() {
        return of(ShaclSeverity.FATAL).isEmpty();
    }

    /** The gaps worth asking the user about. */
    public List<ShaclFinding> clarificationCandidates() {
        return of(ShaclSeverity.CLARIFICATION_CANDIDATE);
    }
}
