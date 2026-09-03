package org.on7o.server.hcin;

import jakarta.annotation.PostConstruct;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Makes the ego a node the network can actually reach.
 *
 * <p>The ego used to be a URI in a configuration file and nothing else: no type,
 * no name, no statement anywhere pointing at it. Everything the ego did was
 * recorded against a second person, minted from the name a thought used, and a
 * projection centred on the configured URI came back with one node and no edges
 * while the network was full of the ego's relationships.
 *
 * <p>Asserted rather than inferred: who the network belongs to is not a guess.
 * Written on every start, so changing the name in the configuration renames the
 * ego instead of leaving the old name behind.
 */
@Component
public class EgoIdentity {

    private static final Logger log = LoggerFactory.getLogger(EgoIdentity.class);

    private final HcinRepository repository;

    public EgoIdentity(HcinRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void assertEgo() {
        Model model = ModelFactory.createDefaultModel();
        Resource ego = model.createResource(repository.ego());
        model.add(ego, RDF.type, HcinVocabulary.PERSON);
        model.add(ego, HcinVocabulary.LABEL, repository.egoLabel());

        repository.add(HcinGraphs.ASSERTED, model);
        log.info("ego is {} , known as \"{}\"", repository.ego(), repository.egoLabel());
    }
}
