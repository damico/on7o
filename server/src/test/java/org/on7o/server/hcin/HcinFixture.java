package org.on7o.server.hcin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;

import java.io.IOException;

/**
 * Builds an HCIN backed by an in-memory dataset, for tests that need a real
 * store rather than a mock of one.
 *
 * <p>The queries under test are SPARQL, so mocking the repository would test
 * nothing. This gives every test its own dataset instead, which starts empty and
 * disappears with the test.
 */
public final class HcinFixture {

    private final HcinDataset dataset;
    private final HcinRepository repository;

    public HcinFixture() throws IOException {
        this(new HcinProperties().getEgoLabel());
    }

    /** A fixture whose ego answers to a given name, for tests about identity. */
    public HcinFixture(String egoLabel) throws IOException {
        HcinProperties properties = new HcinProperties();
        properties.setLocation(HcinDataset.IN_MEMORY);
        properties.setEgoLabel(egoLabel);

        this.dataset = new HcinDataset(properties);
        this.repository = new HcinRepository(new HcinTransactions(dataset), dataset);
    }

    public HcinRepository repository() {
        return repository;
    }

    public HcinDataset dataset() {
        return dataset;
    }

    public String ego() {
        return dataset.ego();
    }

    /** Loads Turtle into a named graph. */
    public void load(String graphUri, String turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFParser.fromString(prefixed(turtle)).lang(Lang.TURTLE).parse(model);
        repository.add(graphUri, model);
    }

    /** Loads Turtle into the asserted graph. */
    public void loadAsserted(String turtle) {
        load(HcinGraphs.ASSERTED, turtle);
    }

    /** The prefixes every fixture would otherwise have to repeat. */
    public static String prefixed(String turtle) {
        return """
                @prefix hcin:  <http://on7o.io/hcin#> .
                @prefix hcinf: <http://on7o.io/hcin/financial#> .
                @prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                @prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .
                @prefix me:    <urn:hcin:person:> .
                @prefix org:   <urn:hcin:org:> .
                @prefix ev:    <urn:hcin:event:> .
                """ + turtle;
    }
}
