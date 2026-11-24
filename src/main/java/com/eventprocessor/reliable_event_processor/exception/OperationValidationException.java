package com.eventprocessor.reliable_event_processor.exception;

public class OperationValidationException extends IllegalArgumentException {
    public OperationValidationException(String message) {
        super(message);
    }
}
