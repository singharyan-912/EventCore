package com.eventprocessor.reliable_event_processor.exception;

public class IdempotencyExpiredException extends RuntimeException {
    public IdempotencyExpiredException(String message) {
        super(message);
    }
}
