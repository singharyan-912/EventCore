package com.eventprocessor.reliable_event_processor.handler;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Common extensible contract for all ReliableFlow business operations.
 *
 * Each handler declares its operation type, validates business payloads,
 * canonicalizes payloads for deterministic SHA-256 fingerprinting,
 * executes side-effects within transaction boundaries, and classifies errors
 * into retryable or permanent failures.
 *
 * @param <R> the structured result type returned by this operation
 */
public interface BusinessOperationHandler<R> {

    /**
     * Unique business operation type identifier (e.g. INVENTORY_DEDUCT, APPOINTMENT_CREATE).
     */
    String getOperationType();

    /**
     * Validates operation-specific payload fields and domain constraints.
     * Throws OperationValidationException or IllegalArgumentException on violation.
     */
    void validate(String tenantId, String resourceId, JsonNode payload);

    /**
     * Executes the business effect inside an active database transaction.
     */
    R execute(String tenantId, String idempotencyKey, String resourceId, JsonNode payload);

    /**
     * Produces a canonical string representation of the operation input for deterministic hashing.
     * Prevents fingerprint instability caused by arbitrary JSON key ordering.
     */
    String canonicalizePayload(String resourceId, JsonNode payload);

    /**
     * Re-executes the business operation during scheduled retry or reprocessing.
     * Defaults to delegating to execute().
     */
    default R executeRetry(String tenantId, String idempotencyKey, String resourceId, JsonNode payload) {
        return execute(tenantId, idempotencyKey, resourceId, payload);
    }

    /**
     * Classifies whether a failure encountered during execution should be retried.
     * Permanent failures (validation, missing resource, domain violations) return false.
     * Transient failures (database timeouts, simulated failures) return true.
     */
    boolean isRetryable(Exception ex);

    /**
     * Serializes the operation result into a JsonNode for structured response rendering.
     */
    JsonNode serializeResult(R result);

    /**
     * Untyped serialization helper for generic dispatcher contexts.
     */
    @SuppressWarnings("unchecked")
    default JsonNode serializeResultUntyped(Object result) {
        return serializeResult((R) result);
    }
}
