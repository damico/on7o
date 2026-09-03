package org.on7o.server.hcin;

import jakarta.annotation.PreDestroy;
import org.apache.jena.dboe.base.file.Location;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * The HCIN dataset itself: a TDB2 store that outlives the process.
 *
 * <p>Per-thought Turtle files record what one thought said. This is where those
 * become a network that can be asked questions across thoughts, and it survives
 * restarts because a network that is rebuilt from scratch each time cannot have
 * a history.
 *
 * <p>The schema graph is loaded from the packaged vocabularies on startup, so
 * the definitions in the dataset are always the ones this build ships rather
 * than whatever was written into it months ago.
 */
@Component
public class HcinDataset {

    private static final Logger log = LoggerFactory.getLogger(HcinDataset.class);

    /** Location value that asks for a dataset that never touches disk. */
    public static final String IN_MEMORY = "mem";

    /** The vocabularies and shapes loaded into the schema graph. */
    public static final List<String> SCHEMA_ARTIFACTS =
            List.of("hcin-core.ttl", "hcin-financial.ttl", "hcin-shapes.ttl");

    private static final String CLASSPATH_DIR = "hcin/";

    private final Dataset dataset;
    private final HcinProperties properties;

    public HcinDataset(HcinProperties properties) throws IOException {
        this.properties = properties;
        this.dataset = open(properties.getLocation());

        if (properties.isLoadSchema()) {
            loadSchema();
        }
    }

    /** The underlying dataset. Callers go through {@link HcinTransactions}. */
    public Dataset dataset() {
        return dataset;
    }

    /** URI of the ego every projection is centred on. */
    public String ego() {
        return properties.getEgo();
    }

    /** The name the ego is known by. */
    public String egoLabel() {
        return properties.getEgoLabel();
    }

    /**
     * Reads a packaged schema artifact as text.
     *
     * @param name file name, which must be one of {@link #SCHEMA_ARTIFACTS}
     */
    public static String readArtifact(String name) throws IOException {
        if (!SCHEMA_ARTIFACTS.contains(name)) {
            throw new IllegalArgumentException("unknown schema artifact: " + name);
        }
        try (InputStream in = new ClassPathResource(CLASSPATH_DIR + name).getInputStream()) {
            return new String(in.readAllBytes());
        }
    }

    @PreDestroy
    void close() {
        dataset.close();
    }

    private static Dataset open(String location) throws IOException {
        if (IN_MEMORY.equalsIgnoreCase(location)) {
            // A fresh name per instance, so two in-memory datasets in one JVM
            // never quietly turn out to be the same dataset.
            log.info("HCIN dataset is in memory and will not survive this process");
            return TDB2Factory.connectDataset(Location.mem(UUID.randomUUID().toString()));
        }

        Path path = Path.of(location).toAbsolutePath().normalize();
        Files.createDirectories(path);
        log.info("HCIN dataset at {}", path);
        return TDB2Factory.connectDataset(Location.create(path.toString()));
    }

    /**
     * Replaces the schema graph with the packaged vocabularies and shapes.
     *
     * <p>Replaced rather than merged: a term removed from a vocabulary should
     * disappear from the dataset too, and merging would keep it alive forever.
     */
    private void loadSchema() throws IOException {
        dataset.begin(ReadWrite.WRITE);
        try {
            Model schema = dataset.getNamedModel(HcinGraphs.SCHEMA);
            schema.removeAll();

            for (String artifact : SCHEMA_ARTIFACTS) {
                RDFParser.fromString(readArtifact(artifact)).lang(Lang.TURTLE).parse(schema);
            }

            // Counted before committing: the model may not be read once the
            // transaction has ended.
            long statements = schema.size();
            dataset.commit();
            log.info("HCIN schema loaded: {} statements from {}", statements, SCHEMA_ARTIFACTS);
        } catch (RuntimeException e) {
            dataset.abort();
            throw e;
        } finally {
            dataset.end();
        }
    }
}
