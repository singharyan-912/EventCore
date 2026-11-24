package com.eventprocessor.reliable_event_processor.exception;

public class SsrfProtectionException extends IllegalArgumentException {
    public SsrfProtectionException(String message) {
        super(message);
    }
}
