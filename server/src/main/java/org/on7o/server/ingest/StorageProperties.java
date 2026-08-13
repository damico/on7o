package org.on7o.server.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for where captured thoughts are stored and how large a single
 * capture may get.
 */
@ConfigurationProperties(prefix = "on7o.storage")
public class StorageProperties {

    /** Root directory holding one sub-directory per captured thought. */
    private String root = "data/thoughts";

    /** Hard limit for a single capture. 32 MiB is roughly 17 min of 16 kHz mono PCM. */
    private long maxBytes = 32L * 1024 * 1024;

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }
}
