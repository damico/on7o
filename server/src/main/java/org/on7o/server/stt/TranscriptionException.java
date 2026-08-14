package org.on7o.server.stt;

/** Raised when a capture could not be transcribed. Never loses the capture itself. */
public class TranscriptionException extends RuntimeException {

    public TranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public TranscriptionException(String message) {
        super(message);
    }
}
