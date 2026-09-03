package org.on7o.server.hcin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the HCIN lives and whose point of view it is written from.
 */
@ConfigurationProperties(prefix = "on7o.hcin")
public class HcinProperties {

    /** Directory holding the TDB2 dataset. The literal "mem" keeps it in memory. */
    private String location = "data/hcin";

    /** URI of the ego: the person every projection is centred on. */
    private String ego = "urn:hcin:person:me";

    /**
     * The name the ego is known by, which is how a thought that names them is
     * recognized as talking about them.
     *
     * <p>Without it the ego is a URI nothing in the network points at: a thought
     * that says "Ninoska invited me to speak" comes back from consolidation
     * naming a person, that person is minted as an entity of their own, and every
     * edge lands on it while the projection stays centred on an empty node.
     */
    private String egoLabel = "Me";

    /** Whether to load the schema files into the schema graph on startup. */
    private boolean loadSchema = true;

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getEgo() {
        return ego;
    }

    public void setEgo(String ego) {
        this.ego = ego;
    }

    public String getEgoLabel() {
        return egoLabel;
    }

    public void setEgoLabel(String egoLabel) {
        this.egoLabel = egoLabel;
    }

    public boolean isLoadSchema() {
        return loadSchema;
    }

    public void setLoadSchema(boolean loadSchema) {
        this.loadSchema = loadSchema;
    }
}
