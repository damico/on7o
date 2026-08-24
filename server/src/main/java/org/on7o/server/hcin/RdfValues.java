package org.on7o.server.hcin;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Conversions between Java values and their RDF form.
 *
 * <p>Timestamps are always written with an explicit seconds field and in UTC,
 * because {@code xsd:dateTime} requires seconds and because two timestamps that
 * are compared in SPARQL have to be written the same way to compare correctly.
 */
public final class RdfValues {

    private static final DateTimeFormatter XSD_DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private RdfValues() {
    }

    /** An instant as an {@code xsd:dateTime} literal. */
    public static Literal dateTime(Instant instant) {
        return ResourceFactory.createTypedLiteral(XSD_DATE_TIME.format(instant), XSDDatatype.XSDdateTime);
    }

    /** An instant as a node, for binding into a query. */
    public static Node dateTimeNode(Instant instant) {
        return dateTime(instant).asNode();
    }

    /** An amount as an {@code xsd:decimal} literal. */
    public static Literal decimal(BigDecimal value) {
        return ResourceFactory.createTypedLiteral(value.toPlainString(), XSDDatatype.XSDdecimal);
    }

    /** Reads an instant from a solution value, or null when absent or unreadable. */
    public static Instant toInstant(RDFNode node) {
        if (node == null || !node.isLiteral()) {
            return null;
        }
        try {
            return Instant.parse(normalize(node.asLiteral().getLexicalForm()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Reads a decimal from a solution value, or null when absent or unreadable. */
    public static BigDecimal toDecimal(RDFNode node) {
        if (node == null || !node.isLiteral()) {
            return null;
        }
        try {
            return new BigDecimal(node.asLiteral().getLexicalForm());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reads a string from a solution value, or null when absent. */
    public static String toString(RDFNode node) {
        if (node == null) {
            return null;
        }
        return node.isLiteral() ? node.asLiteral().getLexicalForm() : node.asResource().getURI();
    }

    /** Reads a URI from a solution value, or null when it is not a resource. */
    public static String toUri(RDFNode node) {
        return node != null && node.isURIResource() ? node.asResource().getURI() : null;
    }

    /**
     * Accepts the {@code xsd:dateTime} forms that reach us from hand-written
     * Turtle: an offset other than Z, or no offset at all.
     */
    private static String normalize(String lexical) {
        if (lexical.endsWith("Z")) {
            return lexical;
        }
        int offset = Math.max(lexical.lastIndexOf('+'), lexical.lastIndexOf('-'));
        if (offset > 10) {
            return java.time.OffsetDateTime.parse(lexical).toInstant().toString();
        }
        return lexical + "Z";
    }
}
