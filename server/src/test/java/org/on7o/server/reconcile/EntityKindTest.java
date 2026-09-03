package org.on7o.server.reconcile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guessing what a type name means, and knowing when not to guess.
 */
class EntityKindTest {

    @Test
    void readsTheNamesThatMeanAPerson() {
        assertThat(EntityKind.fromTypeName("Person")).isEqualTo(EntityKind.PERSON);
        assertThat(EntityKind.fromTypeName("Pessoa")).isEqualTo(EntityKind.PERSON);
        assertThat(EntityKind.fromTypeName("HumanBeing")).isEqualTo(EntityKind.PERSON);
    }

    @Test
    void readsTheNamesThatMeanAnOrganization() {
        assertThat(EntityKind.fromTypeName("Organization")).isEqualTo(EntityKind.ORGANIZATION);
        assertThat(EntityKind.fromTypeName("Empresa")).isEqualTo(EntityKind.ORGANIZATION);
    }

    @Test
    void refusesToReadOwlStructureAsAPerson() {
        // owl:NamedIndividual says the term is an individual rather than a class,
        // which is a fact about the ontology. It also contains the word individual,
        // which is one of the words that mean a person here, so the substring rule
        // used to turn a knowledge-status token written out in OWL into a human
        // being in the network.
        assertThat(EntityKind.fromTypeName("NamedIndividual")).isEqualTo(EntityKind.OTHER);
        assertThat(EntityKind.fromTypeName("Thing")).isEqualTo(EntityKind.OTHER);
        assertThat(EntityKind.fromTypeName("Class")).isEqualTo(EntityKind.OTHER);
    }

    @Test
    void staysUnsureRatherThanGuessing() {
        assertThat(EntityKind.fromTypeName("Lecture")).isEqualTo(EntityKind.OTHER);
        assertThat(EntityKind.fromTypeName(null)).isEqualTo(EntityKind.OTHER);
    }
}
