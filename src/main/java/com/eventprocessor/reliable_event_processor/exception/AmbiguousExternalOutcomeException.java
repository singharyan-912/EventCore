package com.eventprocessor.reliable_event_processor.exception;

/**
 * Thrown when an external HTTP request was transmitted but timed out or encountered
 * an ambiguous transport termination before receiving an authoritative response.
 *
 * Blind retries of ambiguous outcomes are prohibited to prevent duplicate external side-effects.
 */
public class AmbiguousExternalOutcomeException extends RuntimeException {

    private final String externalReference;
    private final String idempotencyKey;

    public AmbiguousExternalOutcomeException(String message, String idempotencyKey, String externalReference) {
        super(message);
        this.idempotencyKey = idempotencyKey;
        this.externalReference = externalReference;
    }

    public AmbiguousExternalOutcomeException(String message, String idempotencyKey, String externalReference, Throwable cause) {
        super(message, cause);
        this.idempotencyKey = idempotencyKey;
        this.externalReference = externalReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getExternalReference() {
        return externalReference;
    }
}
