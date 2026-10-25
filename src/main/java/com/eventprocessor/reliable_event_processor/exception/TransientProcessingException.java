package com.eventprocessor.reliable_event_processor.exception;

/**
 * Indicates a transient failure that is eligible for retry (e.g., transient lock timeout,
 * temporary network hiccup, downstream service unavailability).
 */
public class TransientProcessingException extends RuntimeException {
    public TransientProcessingException(String message) {
        super(message);
    }

    public TransientProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
