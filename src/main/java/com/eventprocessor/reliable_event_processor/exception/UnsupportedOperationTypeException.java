package com.eventprocessor.reliable_event_processor.exception;

public class UnsupportedOperationTypeException extends IllegalArgumentException {
    public UnsupportedOperationTypeException(String message) {
        super(message);
    }
}
