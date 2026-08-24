package org.on7o.server.ingest;

/**
 * Thrown when an operation names a thought that does not exist.
 *
 * <p>Lives in the domain rather than in a controller so that services can refuse
 * an unknown thought without depending on the web layer.
 */
public class ThoughtNotFoundException extends RuntimeException {

    public ThoughtNotFoundException(String thoughtId) {
        super("thought not found: " + thoughtId);
    }
}
