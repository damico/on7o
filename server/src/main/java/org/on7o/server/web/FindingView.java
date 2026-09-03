package org.on7o.server.web;

import org.on7o.server.clarification.GapState;
import org.on7o.server.hcin.ShaclFinding;
import org.on7o.server.hcin.ShaclSeverity;

/**
 * One SHACL finding, prepared for reading.
 *
 * <p>A finding as SHACL reports it names a node by URI and a property by URI,
 * which says nothing to a person. This adds what the node actually is, in the
 * words of the entities it connects, and the thought that introduced it, so the
 * reader can go back to what was said.
 *
 * @param severity  how much it matters
 * @param message   what the shape had to say
 * @param subject   URI of the node the finding is about
 * @param described the same node in readable form
 * @param property  local name of the property at fault, or null
 * @param thoughtId the thought that first observed the node, or null when none did
 * @param state     where the gap stands as a question, or null when it is not a gap
 */
public record FindingView(
        ShaclSeverity severity,
        String message,
        String subject,
        String described,
        String property,
        String thoughtId,
        GapState state) {

    /** True when this is a knowledge gap rather than a defect. */
    public boolean isGap() {
        return severity == ShaclSeverity.CLARIFICATION_CANDIDATE;
    }

    /** True when this is a defect that makes the data untrustworthy. */
    public boolean isDefect() {
        return severity == ShaclSeverity.FATAL;
    }

    /** Reads a finding into the view, given what the repository knows about the node. */
    public static FindingView of(ShaclFinding finding,
                                 String described,
                                 String thoughtId,
                                 GapState state) {
        return new FindingView(
                finding.severity(),
                finding.message(),
                finding.focusNode(),
                described,
                localName(finding.path()),
                thoughtId,
                state);
    }

    /** SHACL reports a path as {@code <uri>}; only its last segment is worth showing. */
    private static String localName(String path) {
        if (path == null) {
            return null;
        }
        String uri = path.startsWith("<") && path.endsWith(">")
                ? path.substring(1, path.length() - 1)
                : path;
        int cut = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
        return cut < 0 ? uri : uri.substring(cut + 1);
    }
}
