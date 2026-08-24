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

    public boolean isLoadSchema() {
        return loadSchema;
    }

    public void setLoadSchema(boolean loadSchema) {
        this.loadSchema = loadSchema;
    }
}
