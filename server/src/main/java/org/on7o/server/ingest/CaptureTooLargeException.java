package org.on7o.server.ingest;

/** Raised when a single capture exceeds {@code on7o.storage.max-bytes}. */
public class CaptureTooLargeException extends RuntimeException {

    public CaptureTooLargeException(long maxBytes) {
        super("capture exceeds the configured limit of " + maxBytes + " bytes");
    }
}
