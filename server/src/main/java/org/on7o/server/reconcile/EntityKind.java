package org.on7o.server.reconcile;

import org.on7o.server.hcin.HcinVocabulary;
import org.apache.jena.rdf.model.Resource;

/**
 * What sort of thing a candidate entity is.
 *
 * <p>Only three, because only three matter to the financial projection: people,
 * the organizations that give their relationships a context, and everything else
 * a thought happened to mention.
 */
public enum EntityKind {

    PERSON,
    ORGANIZATION,
    OTHER;

    /**
     * Guesses the kind from the local name of a type.
     *
     * <p>The consolidated ontology is written by a language model against a
     * loose vocabulary, so its type names are suggestive rather than
     * authoritative. Anything unrecognised stays {@link #OTHER} instead of being
     * forced into a class it may not belong to.
     */
    public static EntityKind fromTypeName(String localName) {
        if (localName == null) {
            return OTHER;
        }
        String name = localName.toLowerCase();
        if (name.contains("person") || name.contains("people") || name.contains("human")
                || name.contains("pessoa") || name.contains("individual")) {
            return PERSON;
        }
        if (name.contains("organization") || name.contains("organisation") || name.contains("company")
                || name.contains("empresa") || name.contains("organizacao") || name.contains("institution")) {
            return ORGANIZATION;
        }
        return OTHER;
    }

    /** The HCIN class this kind maps to. */
    public Resource hcinClass() {
        return switch (this) {
            case PERSON -> HcinVocabulary.PERSON;
            case ORGANIZATION -> HcinVocabulary.ORGANIZATION;
            case OTHER -> HcinVocabulary.ENTITY;
        };
    }

    /** The path segment entities of this kind are minted under. */
    public String segment() {
        return switch (this) {
            case PERSON -> "person";
            case ORGANIZATION -> "org";
            case OTHER -> "entity";
        };
    }
}
