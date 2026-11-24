package com.eventprocessor.reliable_event_processor.exception;

public class PermanentOperationException extends RuntimeException {
    public PermanentOperationException(String message) {
        super(message);
    }

    public PermanentOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
