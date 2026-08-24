package org.on7o.server.hcin;

/**
 * What a SHACL result means for the HCIN.
 *
 * <p>The three levels are not degrees of the same thing. A violation says the
 * data is wrong; a warning says it is thin; a clarification candidate says
 * something is missing that a person could be asked about, which is not a defect
 * at all but the pipeline working as intended.
 */
public enum ShaclSeverity {

    /** The data breaks the model and must not be trusted. */
    FATAL,

    /** The data is usable but incomplete. */
    WARNING,

    /** A knowledge gap worth turning into a question for the user. */
    CLARIFICATION_CANDIDATE;

    private static final String SHACL_NS = "http://www.w3.org/ns/shacl#";

    /** Reads the level from a SHACL severity URI. */
    public static ShaclSeverity of(String uri) {
        if (uri == null) {
            return WARNING;
        }
        return switch (uri.substring(uri.lastIndexOf('#') + 1)) {
            case "Violation" -> FATAL;
            case "Info" -> CLARIFICATION_CANDIDATE;
            default -> WARNING;
        };
    }

    /** The SHACL URI this level corresponds to. */
    public String uri() {
        return SHACL_NS + switch (this) {
            case FATAL -> "Violation";
            case WARNING -> "Warning";
            case CLARIFICATION_CANDIDATE -> "Info";
        };
    }
}
