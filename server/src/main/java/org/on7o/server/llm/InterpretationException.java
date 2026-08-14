package org.on7o.server.llm;

/**
 * Thrown when any stage of the LLM-based thought interpretation pipeline fails.
 */
public class InterpretationException extends RuntimeException {

    public InterpretationException(String message) {
        super(message);
    }

    public InterpretationException(String message, Throwable cause) {
        super(message, cause);
    }
}
