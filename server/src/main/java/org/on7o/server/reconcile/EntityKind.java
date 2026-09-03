package org.on7o.server.reconcile;

import org.on7o.server.hcin.HcinVocabulary;
import org.apache.jena.rdf.model.Resource;

import java.util.Set;

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
     * Type names that say where something sits in the vocabulary rather than what
     * it is in the world.
     *
     * <p>They are excluded before any guessing, because guessing on them is
     * actively wrong. {@code owl:NamedIndividual} is the OWL way of saying "this
     * is an individual, not a class", and it contains the word individual, which
     * is one of the words that mean a person here. Left to the substring rule, a
     * knowledge-status token written out in OWL would enter the network as a
     * human being.
     */
    private static final Set<String> VOCABULARY_NAMES = Set.of(
            "namedindividual", "thing", "resource", "class", "property",
            "objectproperty", "datatypeproperty", "annotationproperty", "ontology");

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
        if (VOCABULARY_NAMES.contains(name)) {
            return OTHER;
        }
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
