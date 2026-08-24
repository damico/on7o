package org.on7o.server.hcin;

/**
 * One thing SHACL had to say about the data.
 *
 * @param severity  how much it matters
 * @param focusNode the resource the finding is about
 * @param path      the property at fault, or null when the finding is about the node itself
 * @param message   what is wrong, in the words of the shape that found it
 * @param shape     the shape that produced it
 */
public record ShaclFinding(
        ShaclSeverity severity,
        String focusNode,
        String path,
        String message,
        String shape) {
}
